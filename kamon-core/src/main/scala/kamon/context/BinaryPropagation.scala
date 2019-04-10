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
import kamon.context.generated.binary.context.{Context => ColferContext, Entry => ColferEntry, Tags => ColferTags}
import kamon.context.generated.binary.context.{StringTag => ColferStringTag, LongTag => ColferLongTag, BooleanTag => ColferBooleanTag}
import kamon.tag.{Tag, TagSet}
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


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
    private val _logger = LoggerFactory.getLogger(classOf[BinaryPropagation.Default])
    private val _streamPool = new ThreadLocal[Default.ReusableByteStreamWriter] {
      override def initialValue(): Default.ReusableByteStreamWriter = new Default.ReusableByteStreamWriter(128)
    }

    private val _contextBufferPool = new ThreadLocal[Array[Byte]] {
      override def initialValue(): Array[Byte] = Array.ofDim[Byte](settings.maxOutgoingSize)
    }

    override def read(reader: ByteStreamReader): Context = {
      if(reader.available() > 0) {
        val contextData = Try {
          val cContext = new ColferContext()
          cContext.unmarshal(reader.readAll(), 0)
          cContext
        }

        contextData.failed.foreach {
          case NonFatal(t) => _logger.warn("Failed to read Context from ByteStreamReader", t)
        }

        contextData.map { colferContext =>

          // Context tags
          val tagsBuilder = Map.newBuilder[String, Any]
          if(colferContext.tags != null) {
            colferContext.tags.strings.foreach(t => tagsBuilder += (t.key -> t.value))
            colferContext.tags.longs.foreach(t => tagsBuilder += (t.key -> t.value))
            colferContext.tags.booleans.foreach(t => tagsBuilder += (t.key -> t.value))
          }
          val tags = TagSet.from(tagsBuilder.result())

          // Only reads the entries for which there is a registered reader
          colferContext.entries.foldLeft(Context.of(tags)) {
            case (context, entryData) =>
              settings.incomingEntries.get(entryData.key).map { entryReader =>
                var contextWithEntry = context
                try {
                  contextWithEntry = entryReader.read(ByteStreamReader.of(entryData.value), context)
                } catch {
                  case NonFatal(t) => _logger.warn("Failed to read entry [{}]", entryData.key.asInstanceOf[Any], t)
                }

                contextWithEntry
              }.getOrElse(context)
          }
        } getOrElse(Context.Empty)
      } else Context.Empty
    }

    override def write(context: Context, writer: ByteStreamWriter): Unit = {
      if (context.nonEmpty()) {
        val contextData = new ColferContext()
        val output = _streamPool.get()
        val contextOutgoingBuffer = _contextBufferPool.get()

        if(context.tags.nonEmpty()) {
          val tagsData = new ColferTags()
          val strings = Array.newBuilder[ColferStringTag]
          val longs = Array.newBuilder[ColferLongTag]
          val booleans = Array.newBuilder[ColferBooleanTag]

          context.tags.iterator().foreach {
            case t: Tag.String =>
              val st = new ColferStringTag()
              st.setKey(t.key)
              st.setValue(t.value)
              strings += st

            case t: Tag.Long =>
              val lt = new ColferLongTag()
              lt.setKey(t.key)
              lt.setValue(t.value)
              longs += lt

            case t: Tag.Boolean =>
              val bt = new ColferBooleanTag()
              bt.setKey(t.key)
              bt.setValue(t.value)
              booleans += bt
          }

          tagsData.setStrings(strings.result())
          tagsData.setLongs(longs.result())
          tagsData.setBooleans(booleans.result())
          contextData.setTags(tagsData)
        }


        val entriesBuilder = Array.newBuilder[ColferEntry]
        context.entries().foreach { entry =>
          settings.outgoingEntries.get(entry.key).foreach { entryWriter =>
            val colferEntry = new ColferEntry()
            try {
              output.reset()
              entryWriter.write(context, output)

              colferEntry.key = entry.key
              colferEntry.value = output.toByteArray()
            } catch {
              case NonFatal(t) => _logger.warn("Failed to write entry [{}]", entry.key.asInstanceOf[Any], t)
            }

            entriesBuilder += colferEntry
          }
        }

        contextData.entries = entriesBuilder.result()

        try {
          val contextSize = contextData.marshal(contextOutgoingBuffer, 0)
          writer.write(contextOutgoingBuffer, 0, contextSize)
        } catch {
          case NonFatal(t) => _logger.warn("Failed to write Context to ByteStreamWriter", t)
        }
      }
    }
  }

  object Default {
    private class ReusableByteStreamWriter(size: Int) extends ByteArrayOutputStream(size) with ByteStreamWriter {
      def underlying(): Array[Byte] = this.buf
    }
  }

  case class Settings(
    maxOutgoingSize: Int,
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
        config.getBytes("max-outgoing-size").toInt,
        buildInstances[Propagation.EntryReader[ByteStreamReader]](config.getConfig("entries.incoming").pairs),
        buildInstances[Propagation.EntryWriter[ByteStreamWriter]](config.getConfig("entries.outgoing").pairs)
      )
    }
  }
}
