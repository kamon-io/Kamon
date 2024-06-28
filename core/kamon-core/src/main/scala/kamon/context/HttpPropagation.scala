/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package context

import com.typesafe.config.Config
import kamon.tag.{Tag, TagSet}
import kamon.trace.{Span, SpanPropagation}
import kamon.util.Filter.Glob
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.util.control.NonFatal

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
      */
    def read(header: String): Option[String]

    /**
      * Returns a map with all HTTP headers present in the wrapped HTTP message.
      */
    def readAll(): Map[String, String]
  }

  /**
    * Wrapper that writes HTTP headers to a HTTP message.
    */
  trait HeaderWriter {

    /**
      * Writes a HTTP header into a HTTP message.
      */
    def write(header: String, value: String): Unit
  }

  /**
    * Create a new HTTP propagation instance from the provided configuration.
    */
  def from(
    propagationConfig: Config,
    identifierScheme: String
  ): Propagation[HttpPropagation.HeaderReader, HttpPropagation.HeaderWriter] = {
    new HttpPropagation.Default(Settings.from(propagationConfig, identifierScheme))
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
      val tags = Map.newBuilder[String, Any]

      // Tags encoded together in the context tags header.
      try {
        reader.read(settings.tagsHeaderName).foreach { contextTagsHeader =>
          contextTagsHeader.split(";").foreach(tagData => {
            val tagPair = tagData.split("=")
            if (tagPair.length == 2) {
              tags += (tagPair(0) -> parseTagValue(tagPair(1)))
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

      // apply filter on tags on the exclusion list
      val filteredTags = tags.result().filterNot(kv => tagFilter(kv._1))

      // Incoming Entries
      settings.incomingEntries.foldLeft(Context.of(TagSet.from(filteredTags))) {
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
      context.tags.iterator().filterNot(tagFilter).foreach { tag =>
        val tagKey = tag.key

        settings.tagsMappings.get(tagKey) match {
          case Some(mappedHeader) => writer.write(mappedHeader, tagValueWithPrefix(tag))
          case None               => appendTag(tagKey, Tag.unwrapValue(tag).toString)
        }
      }

      // Write the upstream name, if needed
      if (settings.includeUpstreamName)
        appendTag(Span.TagKeys.UpstreamName, Kamon.environment.service)

      // Write the context tags header.
      if (contextTagsHeader.nonEmpty) {
        writer.write(settings.tagsHeaderName, contextTagsHeader.result())
      }

      // Write entries for the specified direction.
      settings.outgoingEntries.foreach {
        case (entryName, entryWriter) =>
          try {
            entryWriter.write(context, writer)
          } catch {
            case NonFatal(t) =>
              log.warn("Failed to write entry [{}] due to: {}", entryName.asInstanceOf[Any], t.asInstanceOf[Any])
          }
      }
    }

    private val _longTypePrefix = "l:"
    private val _booleanTypePrefix = "b:"

    /**
      * Filter for checking the tag towards the configured filter from ''kamon.propagation.http.default.tags.filter''
      */
    private def tagFilter(tag: Tag): Boolean = tagFilter(tag.key)
    private def tagFilter(tagName: String): Boolean = settings.tagsFilter.exists(_.accept(tagName))

    /**
      * Tries to infer and parse a value into one of the supported tag types: String, Long or Boolean by looking for the
      * type indicator prefix on the tag value. If the inference fails it will default to treat the value as a String.
      */
    private def parseTagValue(value: String): Any = {
      if (value.length < 2) // Empty and short values definitely do not have type indicators.
        value
      else {
        if (value.startsWith(_longTypePrefix)) {
          // Try to parse the content as a Long value.
          val remaining = value.substring(2)
          try {
            remaining.toLong
          } catch {
            case _: java.lang.NumberFormatException => remaining
          }

        } else if (value.startsWith(_booleanTypePrefix)) {
          // Try to parse the content as a Boolean value.
          val remaining = value.substring(2)
          if (remaining == "true")
            true
          else if (remaining == "false")
            false
          else
            remaining

        } else value
      }
    }

    /**
      * Returns the actual value to be written in the HTTP transport, with a type prefix if applicable.
      */
    private def tagValueWithPrefix(tag: Tag): String = tag match {
      case t: Tag.String  => t.value
      case t: Tag.Boolean => _booleanTypePrefix + t.value.toString
      case t: Tag.Long    => _longTypePrefix + t.value.toString
    }

  }

  case class Settings(
    tagsHeaderName: String,
    includeUpstreamName: Boolean,
    tagsFilter: Seq[Glob],
    tagsMappings: Map[String, String],
    incomingEntries: Map[String, Propagation.EntryReader[HeaderReader]],
    outgoingEntries: Map[String, Propagation.EntryWriter[HeaderWriter]]
  )

  object Settings {
    type ReaderWriter = Propagation.EntryReader[HeaderReader] with Propagation.EntryWriter[HeaderWriter]

    private val log = LoggerFactory.getLogger(classOf[HttpPropagation.Settings])
    private val readerWriterShortcuts: Map[String, ReaderWriter] = Map(
      "span/b3" -> SpanPropagation.B3(),
      "span/uber" -> SpanPropagation.Uber(),
      "span/b3-single" -> SpanPropagation.B3Single(),
      "span/w3c" -> SpanPropagation.W3CTraceContext(),
      "span/datadog" -> SpanPropagation.DataDog()
    )

    def from(config: Config, identifierScheme: String): Settings = {
      def buildInstances[ExpectedType: ClassTag](mappings: Map[String, String]): Map[String, ExpectedType] = {
        val instanceMap = Map.newBuilder[String, ExpectedType]

        mappings.foreach {
          case (contextKey, componentClass) =>
            val shortcut = s"$contextKey/$componentClass".toLowerCase()

            if (shortcut == "span/w3c" && identifierScheme != "double") {
              log.warn("W3C TraceContext propagation should be used only with identifier-scheme = double")
            }

            readerWriterShortcuts.get(shortcut).fold({
              try {
                instanceMap += (contextKey -> ClassLoading.createInstance[ExpectedType](componentClass, Nil))
              } catch {
                case exception: Exception => log.warn(
                    "Failed to instantiate {} [{}] due to []",
                    implicitly[ClassTag[ExpectedType]].runtimeClass.getName,
                    componentClass,
                    exception
                  )
              }
            })(readerWriter => instanceMap += (contextKey -> readerWriter.asInstanceOf[ExpectedType]))
        }

        instanceMap.result()
      }

      val tagsHeaderName = config.getString("tags.header-name")
      val tagsIncludeUpstreamName = config.getBoolean("tags.include-upstream-name")
      val tagsFilter = config.getStringList("tags.filter").asScala.map(Glob).toSeq
      val tagsMappings = config.getConfig("tags.mappings").pairs
      val incomingEntries =
        buildInstances[Propagation.EntryReader[HeaderReader]](config.getConfig("entries.incoming").pairs)
      val outgoingEntries =
        buildInstances[Propagation.EntryWriter[HeaderWriter]](config.getConfig("entries.outgoing").pairs)

      Settings(tagsHeaderName, tagsIncludeUpstreamName, tagsFilter, tagsMappings, incomingEntries, outgoingEntries)
    }
  }

}
