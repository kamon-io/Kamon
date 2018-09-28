/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon
package context

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}

import com.typesafe.config.Config
import kamon.context.BinaryPropagation.{ByteStreamReader, ByteStreamWriter}
import kamon.context.generated.binary.context.{Context => ColferContext, Entry => ColferEntry}
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


/**
  * Context propagation that uses byte stream abstractions as the transport medium. The Binary propagation uses
  * instances of [[ByteStreamReader]] and [[ByteStreamWriter]] to decode and encode Context instances, respectively.
  *
  * Binary propagation uses the [[ByteStreamReader]] and [[ByteStreamWriter]] abstraction which closely model the APIs
  * from [[InputStream]] and [[OutputStream]], but without exposing additional functionality that wouldn't have any
  * well defined behavior for Context propagation, e.g. flush or close functions on OutputStreams.
  */
object BinaryPropagation {

  /**
    * Represents a readable stream of bytes. This interface closely resembles [[InputStream]], minus the functionality
    * that wouldn't have a clearly defined behavior in the context of Context propagation.
    */
  trait ByteStreamReader {
    /**
      * Number of available bytes on the ByteStream.
      */
    def available(): Int

    /**
      * Reads as many bytes as possible into the target byte array.
      *
      * @param target Target buffer in which the read bytes will be written.
      * @return The number of bytes written into the target buffer.
      */
    def read(target: Array[Byte]): Int

    /**
      * Reads a specified number of bytes into the target buffer, starting from the offset position.
      *
      * @param target Target buffer in which read bytes will be written.
      * @param offset Offset index in which to start writing bytes on the target buffer.
      * @param count Number of bytes to be read.
      * @return The number of bytes written into the target buffer.
      */
    def read(target: Array[Byte], offset: Int, count: Int): Int

    /**
      * Reads all available bytes into a newly created byte array.
      *
      * @return All bytes read.
      */
    def readAll(): Array[Byte]
  }


  object ByteStreamReader {

    /**
      * Creates a new [[ByteStreamReader]] that reads data from a byte array.
      */
    def of(bytes: Array[Byte]): ByteStreamReader = new ByteArrayInputStream(bytes) with ByteStreamReader {
      override def readAll(): Array[Byte] = {
        val target = Array.ofDim[Byte](available())
        read(target, 0, available())
        target
      }
    }
  }


  /**
    * Represents a writable stream of bytes. This interface closely resembles [[OutputStream]], minus the functionality
    * that wouldn't have a clearly defined behavior in the context of Context propagation.
    */
  trait ByteStreamWriter {

    /**
      * Writes all bytes into the stream.
      */
    def write(bytes: Array[Byte]): Unit

    /**
      * Writes a portion of the provided bytes into the stream.
      *
      * @param bytes Buffer from which data will be selected.
      * @param offset Starting index on the buffer.
      * @param count Number of bytes to write into the stream.
      */
    def write(bytes: Array[Byte], offset: Int, count: Int): Unit

    /**
      * Write a single byte into the stream.
      */
    def write(byte: Int): Unit

  }

  object ByteStreamWriter {

    /**
      * Creates a new [[ByteStreamWriter]] from an OutputStream.
      */
    def of(outputStream: OutputStream): ByteStreamWriter = new ByteStreamWriter {
      override def write(bytes: Array[Byte]): Unit =
        outputStream.write(bytes)

      override def write(bytes: Array[Byte], offset: Int, count: Int): Unit =
        outputStream.write(bytes, offset, count)

      override def write(byte: Int): Unit =
        outputStream.write(byte)
    }
  }



  /**
    * Create a new default Binary Propagation instance from the provided configuration.
    *
    * @param config Binary Propagation channel configuration
    * @return A newly constructed HttpPropagation instance.
    */
  def from(config: Config, classLoading: ClassLoading): Propagation[ByteStreamReader, ByteStreamWriter] = {
    new BinaryPropagation.Default(Settings.from(config, classLoading))
  }

  /**
    * Default Binary propagation in Kamon. This implementation uses Colfer to read and write the context tags and
    * entries. Entries are represented as simple pairs of entry name and bytes, which are then processed by the all
    * configured entry readers and writers.
    */
  class Default(settings: Settings) extends Propagation[ByteStreamReader, ByteStreamWriter] {
    private val _log = LoggerFactory.getLogger(classOf[BinaryPropagation.Default])
    private val _streamPool = new ThreadLocal[Default.ReusableByteStreamWriter] {
      override def initialValue(): Default.ReusableByteStreamWriter = new Default.ReusableByteStreamWriter(128)
    }

    override def read(reader: ByteStreamReader): Context = {
      if(reader.available() > 0) {
        val contextData = new ColferContext()
        contextData.unmarshal(reader.readAll(), 0)

        // Context tags
        var tagSectionsCount = contextData.tags.length
        if (tagSectionsCount > 0 && tagSectionsCount % 2 != 0) {
          _log.warn("Malformed context tags found when trying to read a Context from ByteStreamReader")
          tagSectionsCount -= 1
        }

        val tags = if (tagSectionsCount > 0) {
          val tagsBuilder = Map.newBuilder[String, String]
          var tagIndex = 0
          while (tagIndex < tagSectionsCount) {
            tagsBuilder += (contextData.tags(tagIndex) -> contextData.tags(tagIndex + 1))
            tagIndex += 2
          }
          tagsBuilder.result()

        } else Map.empty[String, String]


        // Only reads the entries for which there is a registered reader
        contextData.entries.foldLeft(Context.of(tags)) {
          case (context, entryData) =>
            settings.incomingEntries.get(entryData.name).map { entryReader =>
              var contextWithEntry = context
              try {
                contextWithEntry = entryReader.read(ByteStreamReader.of(entryData.content), context)
              } catch {
                case NonFatal(t) => _log.warn("Failed to read entry [{}]", entryData.name.asInstanceOf[Any], t)
              }

              contextWithEntry
            }.getOrElse(context)
        }
      } else Context.Empty
    }

    override def write(context: Context, writer: ByteStreamWriter): Unit = {
      if (context.nonEmpty()) {
        val contextData = new ColferContext()
        val output = _streamPool.get()

        if (context.tags.nonEmpty) {
          val tags = Array.ofDim[String](context.tags.size * 2)
          var tagIndex = 0
          context.tags.foreach {
            case (key, value) =>
              tags.update(tagIndex, key)
              tags.update(tagIndex + 1, value)
              tagIndex += 2
          }

          contextData.tags = tags
        }

        if (context.entries.nonEmpty) {
          val entries = settings.outgoingEntries.collect {
            case (entryName, entryWriter) if context.entries.contains(entryName) =>
              output.reset()
              entryWriter.write(context, output)

              val colferEntry = new ColferEntry()
              colferEntry.name = entryName
              colferEntry.content = output.toByteArray()
              colferEntry
          }

          contextData.entries = entries.toArray
        }

        output.reset()
        // TODO: avoid internal allocation of byte[] on the marshal method. Use ReusableByteStreamWriter's underlying buffer.
        contextData.marshal(output, null)
        writer.write(output.toByteArray)
      }
    }
  }

  object Default {
    private class ReusableByteStreamWriter(size: Int) extends ByteArrayOutputStream(size) with ByteStreamWriter {
      def underlying(): Array[Byte] = this.buf
    }
  }

  case class Settings(
    incomingEntries: Map[String, Propagation.EntryReader[ByteStreamReader]],
    outgoingEntries: Map[String, Propagation.EntryWriter[ByteStreamWriter]]
  )

  object Settings {
    private val log = LoggerFactory.getLogger(classOf[BinaryPropagation.Settings])

    def from(config: Config, classLoading: ClassLoading): BinaryPropagation.Settings = {
      def buildInstances[ExpectedType : ClassTag](mappings: Map[String, String]): Map[String, ExpectedType] = {
        val instanceMap = Map.newBuilder[String, ExpectedType]

        mappings.foreach {
          case (contextKey, componentClass) => classLoading.createInstance[ExpectedType](componentClass, Nil) match {
            case Success(componentInstance) => instanceMap += (contextKey -> componentInstance)
            case Failure(exception) => log.warn("Failed to instantiate {} [{}] due to []",
              implicitly[ClassTag[ExpectedType]].runtimeClass.getName, componentClass, exception)
          }
        }

        instanceMap.result()
      }

      Settings(
        buildInstances[Propagation.EntryReader[ByteStreamReader]](config.getConfig("entries.incoming").pairs),
        buildInstances[Propagation.EntryWriter[ByteStreamWriter]](config.getConfig("entries.outgoing").pairs)
      )
    }
  }
}
