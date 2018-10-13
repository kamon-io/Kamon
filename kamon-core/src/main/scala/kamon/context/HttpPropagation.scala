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

import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
  * Context propagation that uses HTTP headers as the transport medium. HTTP propagation mechanisms read any number of
  * HTTP headers from incoming HTTP requests to decode a Context instance and write any number of HTTP headers on
  * outgoing requests to transfer a context to remote services.
  */
object HttpPropagation {

  /**
    * Wrapper that reads HTTP headers from a HTTP message.
    */
  trait HeaderReader {

    /**
      * Reads a single HTTP header value.
      *
      * @param header HTTP header name
      * @return The HTTP header value, if present.
      */
    def read(header: String): Option[String]

    /**
      * Reads all HTTP headers present in the wrapped HTTP message.
      *
      * @return A map from header name to
      */
    def readAll(): Map[String, String]
  }

  /**
    * Wrapper that writes HTTP headers to a HTTP message.
    */
  trait HeaderWriter {

    /**
      * Writes a HTTP header into a HTTP message.
      *
      * @param header HTTP header name.
      * @param value HTTP header value.
      */
    def write(header: String, value: String): Unit
  }



  /**
    * Create a new default HTTP propagation instance from the provided configuration.
    *
    * @param config HTTP propagation channel configuration
    * @return A newly constructed HttpPropagation instance.
    */
  def from(config: Config, classLoading: ClassLoading): Propagation[HttpPropagation.HeaderReader, HttpPropagation.HeaderWriter] = {
    new HttpPropagation.Default(Settings.from(config, classLoading))
  }

  /**
    * Default HTTP Propagation in Kamon.
    */
  class Default(settings: Settings) extends Propagation[HttpPropagation.HeaderReader, HttpPropagation.HeaderWriter] {
    private val log = LoggerFactory.getLogger(classOf[HttpPropagation.Default])

    /**
      * Reads context tags and entries on the following order:
      *   1. Read all context tags from the context tags header.
      *   2. Read all context tags with explicit mappings. This overrides any tag
      *      from the previous step in case of a tag key clash.
      *   3. Read all context entries using the incoming entries configuration.
      */
    override def read(reader: HeaderReader): Context = {
      val tags = Map.newBuilder[String, String]

      // Tags encoded together in the context tags header.
      try {
        reader.read(settings.tagsHeaderName).foreach { contextTagsHeader =>
          contextTagsHeader.split(";").foreach(tagData => {
            val tagPair = tagData.split("=")
            if (tagPair.length == 2) {
              tags += (tagPair(0) -> tagPair(1))
            }
          })
        }
      } catch {
        case NonFatal(t) => log.warn("Failed to read the context tags header", t.asInstanceOf[Any])
      }

      // Tags explicitly mapped on the tags.mappings configuration.
      settings.tagsMappings.foreach {
        case (tagName, httpHeader) =>
          try {
            reader.read(httpHeader).foreach(tagValue => tags += (tagName -> tagValue))
          } catch {
            case NonFatal(t) => log.warn("Failed to read mapped tag [{}]", tagName, t.asInstanceOf[Any])
          }
      }

      // Incoming Entries
      settings.incomingEntries.foldLeft(Context.of(tags.result())) {
        case (context, (entryName, entryDecoder)) =>
          var result = context
          try {
            result = entryDecoder.read(reader, context)
          } catch {
            case NonFatal(t) => log.warn("Failed to read entry [{}]", entryName.asInstanceOf[Any], t.asInstanceOf[Any])
          }

          result
      }
    }

    /**
      * Writes context tags and entries
      */
    override def write(context: Context, writer: HeaderWriter): Unit = {
      val contextTagsHeader = new StringBuilder()
      def appendTag(key: String, value: String): Unit = {
        contextTagsHeader
          .append(key)
          .append('=')
          .append(value)
          .append(';')
      }

      // Write tags with specific mappings or append them to the context tags header.
      context.tags.foreach {
        case (tagKey, tagValue) => settings.tagsMappings.get(tagKey) match {
          case Some(mappedHeader) => writer.write(mappedHeader, tagValue)
          case None => appendTag(tagKey, tagValue)
        }
      }

      // Write the context tags header.
      if(contextTagsHeader.nonEmpty) {
        writer.write(settings.tagsHeaderName, contextTagsHeader.result())
      }

      // Write entries for the specified direction.
      settings.outgoingEntries.foreach {
        case (entryName, entryWriter) =>
          try {
            entryWriter.write(context, writer)
          } catch {
            case NonFatal(t) => log.warn("Failed to write entry [{}] due to: {}", entryName.asInstanceOf[Any], t.asInstanceOf[Any])
          }
      }
    }
  }

  case class Settings(
    tagsHeaderName: String,
    tagsMappings: Map[String, String],
    incomingEntries: Map[String, Propagation.EntryReader[HeaderReader]],
    outgoingEntries: Map[String, Propagation.EntryWriter[HeaderWriter]]
  )

  object Settings {
    private val log = LoggerFactory.getLogger(classOf[HttpPropagation.Settings])

    def from(config: Config, classLoading: ClassLoading): Settings = {
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

      val tagsHeaderName = config.getString("tags.header-name")
      val tagsMappings = config.getConfig("tags.mappings").pairs
      val incomingEntries = buildInstances[Propagation.EntryReader[HeaderReader]](config.getConfig("entries.incoming").pairs)
      val outgoingEntries = buildInstances[Propagation.EntryWriter[HeaderWriter]](config.getConfig("entries.outgoing").pairs)

      Settings(tagsHeaderName, tagsMappings, incomingEntries, outgoingEntries)
    }
  }

}
