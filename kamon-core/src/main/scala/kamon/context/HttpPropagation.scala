package kamon
package context

import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag
import scala.util.{Failure, Success}

/**
  * Context Propagation for HTTP transports. When using HTTP transports all the context related information is
  * read from and written to HTTP headers. The context information may be included in the following directions:
  *   - Incoming: Used for HTTP requests coming into this service. Implicitly used when using HttpPropagation.read.
  *   - Outgoing: Used for HTTP requests leaving this service.
  *   - Returning: Used for HTTP responses send back to clients of this service.
  */
trait HttpPropagation {

  /**
    * Uses the provided [[HttpPropagation.HeaderReader]] to read as many HTTP Headers as necessary and create a
    * [[Context]] instance. The way in which context tags and entries are read from and written to HTTP Headers is
    * implementation specific.
    *
    * @param reader Wrapper on the HTTP message from which headers are read.
    * @return The decoded Context instance. If no entries or tags could be read from the HTTP message then an
    *          empty context is returned instead.
    */
  def readContext(reader: HttpPropagation.HeaderReader): Context

  /**
    * Writes the tags and entries from the supplied context using the supplied [[HttpPropagation.HeaderWriter]]
    * instance. The way in which context tags and entries are read from and written to HTTP Headers is implementation
    * specific.
    *
    * Implementations are expected to produce side effects on the wrapped HTTP Messages.
    *
    * @param context Context instance to be written.
    * @param writer Wrapper on the HTTP message that will carry the context headers.
    * @param direction Write direction. It can be either Outgoing or Returning.
    */
  def writeContext(context: Context, writer: HttpPropagation.HeaderWriter, direction: HttpPropagation.Direction.Write): Unit

}

object HttpPropagation {

  /**
    * Encapsulates logic required to read a single context entry from HTTP headers. Implementations of this trait
    * must be aware of the entry they are able to read and the HTTP headers required to do so.
    */
  trait EntryReader {

    /**
      * Tries to read a context entry from HTTP headers. If a context entry is successfully read, implementations
      * must return an updated context instance that includes such entry. If no entry could be read simply return
      * context instance that was passed in, untouched.
      *
      * @param reader Wrapper on the HTTP message from which headers are read.
      * @param context Current context.
      * @return Either the original context passed in or a modified version of it, including the read entry.
      */
    def readEntry(reader: HttpPropagation.HeaderReader, context: Context): Context
  }

  /**
    * Encapsulates logic required to write a single context entry to HTTP headers. Implementations of this trait
    * must be aware of the entry they are able to write and the HTTP headers required to do so.
    */
  trait EntryWriter {

    /**
      * Tries to write a context entry into HTTP headers.
      *
      * @param context The context from which entries should be written.
      * @param writer Wrapper on the HTTP message that will carry the context headers.
      * @param direction Write direction. It can be either Outgoing or Returning.
      */
    def writeEntry(context: Context, writer: HttpPropagation.HeaderWriter, direction: Direction.Write): Unit
  }


  /**
    * Wrapper that reads HTTP headers from HTTP a message.
    */
  trait HeaderReader {

    /**
      * Reads an HTTP header value
      *
      * @param header HTTP header name
      * @return The HTTP header value, if present.
      */
    def readHeader(header: String): Option[String]
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
    def writeHeader(header: String, value: String): Unit
  }


  /**
    * Create a new default HttpPropagation instance from the provided configuration.
    *
    * @param config HTTP propagation channel configuration
    * @return A newly constructed HttpPropagation instance.
    */
  def from(config: Config, classLoading: ClassLoading): HttpPropagation = {
    new HttpPropagation.Default(Components.from(config, classLoading))
  }

  /**
    * Default HTTP Propagation in Kamon.
    */
  final class Default(components: Components) extends HttpPropagation {
    private val log = LoggerFactory.getLogger(classOf[HttpPropagation.Default])

    /**
      * Reads context tags and entries on the following order:
      *   - Read all context tags from the context tags header.
      *   - Read all context tags with explicit mappings. This overrides any tag from the previous step in case
      *     of a tag key clash.
      *   - Read all context entries using the incoming entries configuration.
      */
    override def readContext(reader: HeaderReader): Context = {
      val tags = Map.newBuilder[String, String]

      // Tags encoded together in the context tags header.
      try {
        reader.readHeader(components.tagsHeaderName).foreach { contextTagsHeader =>
          contextTagsHeader.split(";").foreach(tagData => {
            val tagPair = tagData.split("=")
            if (tagPair.length == 2) {
              tags += (tagPair(0) -> tagPair(1))
            }
          })
        }
      } catch {
        case t: Throwable => log.warn("Failed to read the context tags header", t.asInstanceOf[Any])
      }

      // Tags explicitly mapped on the tags.mappings configuration.
      components.tagsMappings.foreach {
        case (tagName, httpHeader) =>
          try {
            reader.readHeader(httpHeader).foreach(tagValue => tags += (tagName -> tagValue))
          } catch {
            case t: Throwable => log.warn("Failed to read mapped tag [{}]", tagName, t.asInstanceOf[Any])
          }
      }

      // Incoming Entries
      components.incomingEntries.foldLeft(Context.of(tags.result())) {
        case (context, (entryName, entryDecoder)) =>
          var result = context
          try {
            result = entryDecoder.readEntry(reader, context)
          } catch {
            case t: Throwable => log.warn("Failed to read entry [{}]", entryName.asInstanceOf[Any], t.asInstanceOf[Any])
          }

          result
      }
    }

    /**
      * Writes context tags and entries
      */
    override def writeContext(context: Context, writer: HeaderWriter, direction: Direction.Write): Unit = {
      val keys = direction match {
        case Direction.Outgoing => components.outgoingEntries
        case Direction.Returning => components.returningEntries
      }

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
        case (tagKey, tagValue) => components.tagsMappings.get(tagKey) match {
          case Some(mappedHeader) => writer.writeHeader(mappedHeader, tagValue)
          case None => appendTag(tagKey, tagValue)
        }
      }

      // Write the context tags header.
      if(contextTagsHeader.nonEmpty) {
        writer.writeHeader(components.tagsHeaderName, contextTagsHeader.result())
      }

      // Write entries for the specified direction.
      keys.foreach {
        case (entryName, entryWriter) =>
          try {
            entryWriter.writeEntry(context, writer, direction)
          } catch {
            case t: Throwable => log.warn("Failed to write entry [{}] due to: {}", entryName.asInstanceOf[Any], t.asInstanceOf[Any])
          }
      }
    }
  }

  /**
    * Propagation direction. Used to decide whether incoming, outgoing or returning keys must be used to
    * propagate context.
    */
  sealed trait Direction
  object Direction {

    /**
      * Marker trait for all directions that require write operations.
      */
    sealed trait Write

    /**
      * Requests coming into this service.
      */
    case object Incoming extends Direction

    /**
      * Requests going from this service to others.
      */
    case object Outgoing extends Direction with Write

    /**
      * Responses sent from this service to clients.
      */
    case object Returning extends Direction with Write
  }


  case class Components(
    tagsHeaderName: String,
    tagsMappings: Map[String, String],
    incomingEntries: Map[String, HttpPropagation.EntryReader],
    outgoingEntries: Map[String, HttpPropagation.EntryWriter],
    returningEntries: Map[String, HttpPropagation.EntryWriter]
  )

  object Components {
    private val log = LoggerFactory.getLogger(classOf[HttpPropagation.Components])

    def from(config: Config, classLoading: ClassLoading): Components = {
      def buildInstances[ExpectedType : ClassTag](mappings: Map[String, String]): Map[String, ExpectedType] = {
        val entryReaders = Map.newBuilder[String, ExpectedType]

        mappings.foreach {
          case (contextKey, readerClass) => classLoading.createInstance[ExpectedType](readerClass, Nil) match {
            case Success(readerInstance) => entryReaders += (contextKey -> readerInstance)
            case Failure(exception) => log.warn("Failed to instantiate {} [{}] due to []",
              implicitly[ClassTag[ExpectedType]].runtimeClass.getName, readerClass, exception)
          }
        }

        entryReaders.result()
      }

      val tagsHeaderName = config.getString("tags.header-name")
      val tagsMappings = config.getConfig("tags.mappings").pairs
      val incomingEntries = buildInstances[HttpPropagation.EntryReader](config.getConfig("entries.incoming").pairs)
      val outgoingEntries = buildInstances[HttpPropagation.EntryWriter](config.getConfig("entries.outgoing").pairs)
      val returningEntries = buildInstances[HttpPropagation.EntryWriter](config.getConfig("entries.returning").pairs)

      Components(tagsHeaderName, tagsMappings, incomingEntries, outgoingEntries, returningEntries)
    }
  }

}
