package kamon.context

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.HttpPropagation.Direction
import org.scalatest.{Matchers, OptionValues, WordSpec}

import scala.collection.mutable

class HttpPropagationSpec extends WordSpec with Matchers with OptionValues {

  "The HTTP Context Propagation" when {
    "reading from incoming requests" should {
      "return an empty context if there are no tags nor keys" in {
        val context = httpPropagation.read(headerReaderFromMap(Map.empty))
        context.tags shouldBe empty
        context.entries shouldBe empty
      }

      "read tags from an HTTP message when they are available" in {
        val headers = Map(
          "x-content-tags" -> "hello=world;correlation=1234",
          "x-mapped-tag" -> "value"
        )
        val context = httpPropagation.read(headerReaderFromMap(headers))
        context.tags should contain only(
          "hello" -> "world",
          "correlation" -> "1234",
          "mappedTag" -> "value"
        )
      }

      "handle errors when reading HTTP headers" in {
        val headers = Map("fail" -> "")
        val context = httpPropagation.read(headerReaderFromMap(headers))
        context.tags shouldBe empty
        context.entries shouldBe empty
      }

      "read context with entries and tags" in {
        val headers = Map(
          "x-content-tags" -> "hello=world;correlation=1234",
          "string-header" -> "hey",
          "integer-header" -> "123"
        )

        val context = httpPropagation.read(headerReaderFromMap(headers))
        context.get(HttpPropagationSpec.StringKey) shouldBe "hey"
        context.get(HttpPropagationSpec.IntegerKey) shouldBe 123
        context.get(HttpPropagationSpec.OptionalKey) shouldBe empty
        context.getTag("hello").value shouldBe "world"
        context.getTag("correlation").value shouldBe "1234"
        context.getTag("unknown") shouldBe empty
      }
    }


    "writing to outgoing requests" should {
      propagationWritingTests(Direction.Outgoing)
    }

    "writing to returning requests" should {
      propagationWritingTests(Direction.Returning)
    }

    def propagationWritingTests(direction: Direction.Write) = {
      "not write anything if the context is empty" in {
        val headers = mutable.Map.empty[String, String]
        httpPropagation.write(Context.Empty, headerWriterFromMap(headers), direction)
        headers shouldBe empty
      }

      "write context tags when available" in {
        val headers = mutable.Map.empty[String, String]
        val context = Context.of(Map(
          "hello" -> "world",
          "mappedTag" -> "value"
        ))

        httpPropagation.write(context, headerWriterFromMap(headers), direction)
        headers should contain only(
          "x-content-tags" -> "hello=world;",
          "x-mapped-tag" -> "value"
        )
      }

      "write context entries when available" in {
        val headers = mutable.Map.empty[String, String]
        val context = Context.of(
          HttpPropagationSpec.StringKey, "out-we-go",
          HttpPropagationSpec.IntegerKey, 42,
        )

        httpPropagation.write(context, headerWriterFromMap(headers), direction)
        headers should contain only(
          "string-header" -> "out-we-go"
        )
      }
    }
  }




  val httpPropagation = HttpPropagation.from(
    ConfigFactory.parseString(
      """
        |tags {
        |  header-name = "x-content-tags"
        |
        |  mappings {
        |    mappedTag = "x-mapped-tag"
        |  }
        |}
        |
        |entries.incoming.string = "kamon.context.HttpPropagationSpec$StringEntryCodec"
        |entries.incoming.integer = "kamon.context.HttpPropagationSpec$IntegerEntryCodec"
        |entries.outgoing.string = "kamon.context.HttpPropagationSpec$StringEntryCodec"
        |entries.returning.string = "kamon.context.HttpPropagationSpec$StringEntryCodec"
        |
      """.stripMargin
    ).withFallback(ConfigFactory.load().getConfig("kamon.propagation")), Kamon)


  def headerReaderFromMap(map: Map[String, String]): HttpPropagation.HeaderReader = new HttpPropagation.HeaderReader {
    override def read(header: String): Option[String] = {
      if(map.get("fail").nonEmpty)
        sys.error("failing on purpose")

      map.get(header)
    }
  }

  def headerWriterFromMap(map: mutable.Map[String, String]): HttpPropagation.HeaderWriter = new HttpPropagation.HeaderWriter {
    override def write(header: String, value: String): Unit = map.put(header, value)
  }
}

object HttpPropagationSpec {

  val StringKey = Context.key[String]("string", null)
  val IntegerKey = Context.key[Int]("integer", 0)
  val OptionalKey = Context.key[Option[String]]("optional", None)


  class StringEntryCodec extends HttpPropagation.EntryReader with HttpPropagation.EntryWriter {
    private val HeaderName = "string-header"

    override def read(reader: HttpPropagation.HeaderReader, context: Context): Context = {
      reader.read(HeaderName)
        .map(v => context.withKey(StringKey, v))
        .getOrElse(context)
    }

    override def write(context: Context, writer: HttpPropagation.HeaderWriter, direction: Direction.Write): Unit = {
      Option(context.get(StringKey)).foreach(v => writer.write(HeaderName, v))
    }
  }

  class IntegerEntryCodec extends HttpPropagation.EntryReader {
    override def read(reader: HttpPropagation.HeaderReader, context: Context): Context = {
      reader.read("integer-header")
        .map(v => context.withKey(IntegerKey, v.toInt))
        .getOrElse(context)

    }
  }
}