/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

import java.nio.ByteBuffer

import com.typesafe.config.Config
import kamon.util.DynamicAccess
import org.slf4j.LoggerFactory
import kamon.context.generated.binary.context.{Context => ColferContext, Entry => ColferEntry}

import scala.collection.mutable

class Codecs(initialConfig: Config) {
  private val log = LoggerFactory.getLogger(classOf[Codecs])
  @volatile private var httpHeaders: Codecs.ForContext[TextMap] = new Codecs.HttpHeaders(Map.empty)
  @volatile private var binary: Codecs.ForContext[ByteBuffer] = new Codecs.Binary(256, Map.empty)

  reconfigure(initialConfig)


  def HttpHeaders: Codecs.ForContext[TextMap] =
    httpHeaders

  def Binary: Codecs.ForContext[ByteBuffer] =
    binary

  def reconfigure(config: Config): Unit = {
    import scala.collection.JavaConverters._
    try {
      val codecsConfig = config.getConfig("kamon.context.codecs")
      val stringKeys = readStringKeysConfig(codecsConfig.getConfig("string-keys"))
      val knownHttpHeaderCodecs = readEntryCodecs[TextMap]("http-headers-keys", codecsConfig) ++ stringHeaderCodecs(stringKeys)
      val knownBinaryCodecs = readEntryCodecs[ByteBuffer]("binary-keys", codecsConfig) ++ stringBinaryCodecs(stringKeys)

      httpHeaders = new Codecs.HttpHeaders(knownHttpHeaderCodecs)
      binary = new Codecs.Binary(codecsConfig.getBytes("binary-buffer-size"), knownBinaryCodecs)
    } catch {
      case t: Throwable => log.error("Failed to initialize Context Codecs", t)
    }
  }

  private def readEntryCodecs[T](rootKey: String, config: Config): Map[String, Codecs.ForEntry[T]] = {
    val rootConfig = config.getConfig(rootKey)
    val dynamic = new DynamicAccess(getClass.getClassLoader)
    val entries = Map.newBuilder[String, Codecs.ForEntry[T]]

    rootConfig.topLevelKeys.foreach(key => {
      try {
        val fqcn = rootConfig.getString(key)
        entries += ((key, dynamic.createInstanceFor[Codecs.ForEntry[T]](fqcn, Nil).get))
      } catch {
        case e: Throwable =>
          log.error(s"Failed to initialize codec for key [$key]", e)
      }
    })

    entries.result()
  }

  private def readStringKeysConfig(config: Config): Map[String, String] =
    config.topLevelKeys.map(key => (key, config.getString(key))).toMap

  private def stringHeaderCodecs(keys: Map[String, String]): Map[String, Codecs.ForEntry[TextMap]] =
    keys.map { case (key, header) => (key, new Codecs.StringHeadersCodec(key, header)) }

  private def stringBinaryCodecs(keys: Map[String, String]): Map[String, Codecs.ForEntry[ByteBuffer]] =
    keys.map { case (key, _) => (key, new Codecs.StringBinaryCodec(key)) }
}

object Codecs {

  trait ForContext[T] {
    def encode(context: Context): T
    def decode(carrier: T): Context
  }

  trait ForEntry[T] {
    def encode(context: Context): T
    def decode(carrier: T, context: Context): Context
  }

  final class HttpHeaders(entryCodecs: Map[String, Codecs.ForEntry[TextMap]]) extends Codecs.ForContext[TextMap] {
    private val log = LoggerFactory.getLogger(classOf[HttpHeaders])

    override def encode(context: Context): TextMap = {
      val encoded = TextMap.Default()

      context.entries.foreach {
        case (key, _) if key.broadcast =>
          entryCodecs.get(key.name) match {
            case Some(codec) =>
              try {
                codec.encode(context).values.foreach(pair => encoded.put(pair._1, pair._2))
              } catch {
                case e: Throwable => log.error(s"Failed to encode key [${key.name}]", e)
              }

            case None =>
              log.error("Context key [{}] should be encoded in HttpHeaders but no codec was found for it.", key.name)
          }

        case _ => // All non-broadcast keys should be ignored.
      }

      encoded
    }

    override def decode(carrier: TextMap): Context = {
      var context: Context = Context.Empty

      try {
        context = entryCodecs.foldLeft(context)((ctx, codecEntry) => {
          val (_, codec) = codecEntry
          codec.decode(carrier, ctx)
        })

      } catch {
        case e: Throwable =>
          log.error("Failed to decode context from HttpHeaders", e)
      }

      context
    }
  }


  final class Binary(bufferSize: Long, entryCodecs: Map[String, Codecs.ForEntry[ByteBuffer]]) extends Codecs.ForContext[ByteBuffer] {
    private val log = LoggerFactory.getLogger(classOf[Binary])
    private val binaryBuffer = newThreadLocalBuffer(bufferSize)
    private val emptyBuffer = ByteBuffer.allocate(0)

    override def encode(context: Context): ByteBuffer = {
      val entries = context.entries
      if(entries.isEmpty)
        emptyBuffer
      else {
        var colferEntries: List[ColferEntry] = Nil
        entries.foreach {
          case (key, _) if key.broadcast =>
            entryCodecs.get(key.name) match {
              case Some(entryCodec) =>
                try {
                  val entryData = entryCodec.encode(context)
                  if(entryData.capacity() > 0) {
                    val colferEntry = new ColferEntry()
                    colferEntry.setName(key.name)
                    colferEntry.setContent(entryData.array())
                    colferEntries = colferEntry :: colferEntries
                  }
                } catch {
                  case throwable: Throwable =>
                    log.error(s"Failed to encode broadcast context key [${key.name}]", throwable)
                }

              case None =>
                log.error("Failed to encode broadcast context key [{}]. No codec found.", key.name)
            }

          case _ => // All non-broadcast keys should be ignored.
        }

        if(colferEntries.isEmpty)
          emptyBuffer
        else {
          val buffer = binaryBuffer.get()
          val colferContext = new ColferContext()
          colferContext.setEntries(colferEntries.toArray)
          val marshalledSize = colferContext.marshal(buffer, 0)

          val data = Array.ofDim[Byte](marshalledSize)
          System.arraycopy(buffer, 0, data, 0, marshalledSize)
          ByteBuffer.wrap(data)
        }
      }
    }


    override def decode(carrier: ByteBuffer): Context = {
      if(carrier.capacity() == 0)
        Context.Empty
      else {
        var context: Context = Context.Empty

        try {
          val colferContext = new ColferContext()
          colferContext.unmarshal(carrier.array(), 0)

          colferContext.entries.foreach(colferEntry => {
            entryCodecs.get(colferEntry.getName()) match {
              case Some(entryCodec) =>
                context = entryCodec.decode(ByteBuffer.wrap(colferEntry.content), context)

              case None =>
                log.error("Failed to decode entry [{}] with Binary context codec. No entry found for the key.", colferEntry.getName())
            }
          })

        } catch {
          case e: Throwable =>
            log.error("Failed to decode context from Binary", e)
        }

        context
      }
    }


    private def newThreadLocalBuffer(size: Long): ThreadLocal[Array[Byte]] = new ThreadLocal[Array[Byte]] {
      override def initialValue(): Array[Byte] = Array.ofDim[Byte](size.toInt)
    }
  }

  private class StringHeadersCodec(key: String, headerName: String) extends Codecs.ForEntry[TextMap] {
    private val contextKey = Key.broadcast[Option[String]](key, None)

    override def encode(context: Context): TextMap = {
      val textMap = TextMap.Default()
      context.get(contextKey).foreach { value =>
        textMap.put(headerName, value)
      }

      textMap
    }

    override def decode(carrier: TextMap, context: Context): Context = {
      carrier.get(headerName) match {
        case value @ Some(_) => context.withKey(contextKey, value)
        case None            => context
      }
    }
  }

  private class StringBinaryCodec(key: String) extends Codecs.ForEntry[ByteBuffer] {
    val emptyBuffer: ByteBuffer = ByteBuffer.allocate(0)
    private val contextKey = Key.broadcast[Option[String]](key, None)

    override def encode(context: Context): ByteBuffer = {
      context.get(contextKey) match {
        case Some(value)  => ByteBuffer.wrap(value.getBytes)
        case None         => emptyBuffer
      }
    }

    override def decode(carrier: ByteBuffer, context: Context): Context = {
      context.withKey(contextKey, Some(new String(carrier.array())))
    }
  }
}


trait TextMap {

  def get(key: String): Option[String]

  def put(key: String, value: String): Unit

  def values: Iterator[(String, String)]
}

object TextMap {

  class Default extends TextMap {
    private val storage =
      mutable.Map.empty[String, String]

    override def get(key: String): Option[String] =
      storage.get(key)

    override def put(key: String, value: String): Unit =
      storage.put(key, value)

    override def values: Iterator[(String, String)] =
      storage.toIterator
  }

  object Default {
    def apply(): Default =
      new Default()
  }
}