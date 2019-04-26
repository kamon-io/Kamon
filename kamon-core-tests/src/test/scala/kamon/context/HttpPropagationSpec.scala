package kamon.context

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.HttpPropagation.{HeaderReader, HeaderWriter}
import kamon.context.Propagation.{EntryReader, EntryWriter}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import kamon.tag.Lookups._
import kamon.tag.TagSet

import scala.collection.mutable

class HttpPropagationSpec extends WordSpec with Matchers with OptionValues {

  "The HTTP Context Propagation" when {
    "reading from incoming requests" should {
      "return an empty context if there are no tags nor keys" in {
        val context = httpPropagation.read(headerReaderFromMap(Map.empty))
        context.isEmpty() shouldBe true
      }

      "read tags from an HTTP message when they are available" in {
        val headers = Map(
          "x-content-tags" -> "hello=world;correlation=1234",
          "x-mapped-tag" -> "value"
        )

        val context = httpPropagation.read(headerReaderFromMap(headers))
        context.tags.get(plain("hello")) shouldBe "world"
        context.tags.get(plain("correlation")) shouldBe "1234"
        context.tags.get(plain("mappedTag")) shouldBe "value"
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
        context.getTag(plain("hello")) shouldBe "world"
        context.getTag(option("correlation")).value shouldBe "1234"
        context.getTag(option("unknown")) shouldBe empty
      }
    }


    "writing to outgoing requests" should {
      "at least write the upstream name when the context is empty" in {
        val headers = mutable.Map.empty[String, String]
        httpPropagation.write(Context.Empty, headerWriterFromMap(headers))
        headers should contain only(
          "x-content-tags" -> "upstream.name=kamon-application;"
        )
      }

      "write context tags when available" in {
        val headers = mutable.Map.empty[String, String]
        val context = Context.of(TagSet.from(Map(
          "hello" -> "world",
          "mappedTag" -> "value"
        )))

        httpPropagation.write(context, headerWriterFromMap(headers))
        headers should contain only(
          "x-content-tags" -> "hello=world;upstream.name=kamon-application;",
          "x-mapped-tag" -> "value"
        )
      }

      "write context entries when available" in {
        val headers = mutable.Map.empty[String, String]
        val context = Context.of(
          HttpPropagationSpec.StringKey, "out-we-go",
          HttpPropagationSpec.IntegerKey, 42
        )

        httpPropagation.write(context, headerWriterFromMap(headers))
        headers should contain only(
          "x-content-tags" -> "upstream.name=kamon-application;",
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
        |  include-upstream-name = yes
        |
        |  mappings {
        |    mappedTag = "x-mapped-tag"
        |  }
        |}
        |
        |entries.incoming.string = "kamon.context.HttpPropagationSpec$StringEntryCodec"
        |entries.incoming.integer = "kamon.context.HttpPropagationSpec$IntegerEntryCodec"
        |entries.outgoing.string = "kamon.context.HttpPropagationSpec$StringEntryCodec"
        |
      """.stripMargin
    ).withFallback(ConfigFactory.load().getConfig("kamon.propagation")), Kamon)


  def headerReaderFromMap(map: Map[String, String]): HttpPropagation.HeaderReader = new HttpPropagation.HeaderReader {
    override def read(header: String): Option[String] = {
      if(map.get("fail").nonEmpty)
        sys.error("failing on purpose")

      map.get(header)
    }

    override def readAll(): Map[String, String] = map
  }

  def headerWriterFromMap(map: mutable.Map[String, String]): HttpPropagation.HeaderWriter = new HttpPropagation.HeaderWriter {
    override def write(header: String, value: String): Unit = map.put(header, value)
  }
}

object HttpPropagationSpec {

  val StringKey = Context.key[String]("string", null)
  val IntegerKey = Context.key[Int]("integer", 0)
  val OptionalKey = Context.key[Option[String]]("optional", None)


  class StringEntryCodec extends EntryReader[HeaderReader] with EntryWriter[HeaderWriter] {
    private val HeaderName = "string-header"

    override def read(reader: HttpPropagation.HeaderReader, context: Context): Context = {
      reader.read(HeaderName)
        .map(v => context.withKey(StringKey, v))
        .getOrElse(context)
    }

    override def write(context: Context, writer: HttpPropagation.HeaderWriter): Unit = {
      Option(context.get(StringKey)).foreach(v => writer.write(HeaderName, v))
    }
  }

  class IntegerEntryCodec extends EntryReader[HeaderReader] {
    override def read(reader: HttpPropagation.HeaderReader, context: Context): Context = {
      reader.read("integer-header")
        .map(v => context.withKey(IntegerKey, v.toInt))
        .getOrElse(context)

    }
  }
}