package kamon.context

import java.io.ByteArrayOutputStream
import com.typesafe.config.ConfigFactory
import kamon.context.BinaryPropagation.{ByteStreamReader, ByteStreamWriter}
import kamon.context.Propagation.{EntryReader, EntryWriter}
import kamon.tag.TagSet
import kamon.tag.Lookups._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues

import scala.util.Random

class BinaryPropagationSpec extends AnyWordSpec with Matchers with OptionValues {

  "The Binary Context Propagation" should {
    "return an empty context if there is no data to read from" in {
      val context = binaryPropagation.read(ByteStreamReader.of(Array.ofDim[Byte](0)))
      context.isEmpty() shouldBe true
    }

    "not write any data to the medium if the context is empty" in {
      val writer = inspectableByteStreamWriter()
      binaryPropagation.write(Context.Empty, writer)
      writer.size() shouldBe 0
    }

    "handle malformed data in when reading a context" in {
      val randomBytes = Array.ofDim[Byte](42)
      Random.nextBytes(randomBytes)

      val context = binaryPropagation.read(ByteStreamReader.of(randomBytes))
      context.isEmpty() shouldBe true
    }

    "handle read failures in an entry reader" in {
      val context = Context.of(
        BinaryPropagationSpec.StringKey,
        "string-value",
        BinaryPropagationSpec.FailStringKey,
        "fail-read"
      )
      val writer = inspectableByteStreamWriter()
      binaryPropagation.write(context, writer)

      val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
      rtContext.tags.get(plain("upstream.name")) shouldBe "kamon-application"
      rtContext.get(BinaryPropagationSpec.StringKey) shouldBe "string-value"
      rtContext.get(BinaryPropagationSpec.FailStringKey) shouldBe null
    }

    "handle write failures in an entry writer" in {
      val context = Context.of(
        BinaryPropagationSpec.StringKey,
        "string-value",
        BinaryPropagationSpec.FailStringKey,
        "fail-write"
      )
      val writer = inspectableByteStreamWriter()
      binaryPropagation.write(context, writer)

      val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
      rtContext.tags.get(plain("upstream.name")) shouldBe "kamon-application"
      rtContext.get(BinaryPropagationSpec.StringKey) shouldBe "string-value"
      rtContext.get(BinaryPropagationSpec.FailStringKey) shouldBe null
    }

    "handle write failures in an entry writer when the context is too big" in {
      val context = Context.of(BinaryPropagationSpec.StringKey, "string-value" * 20)
      val writer = inspectableByteStreamWriter()
      binaryPropagation.write(context, writer)

      val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
      rtContext shouldBe empty
    }

    "round trip a Context that only has tags" in {
      val context = Context.of(TagSet.from(Map("hello" -> "world", "kamon" -> "rulez")))
      val writer = inspectableByteStreamWriter()
      binaryPropagation.write(context, writer)

      val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
      rtContext.entries shouldBe empty
      rtContext.tags.get(plain("hello")) shouldBe "world"
      rtContext.tags.get(plain("kamon")) shouldBe "rulez"
    }

    "round trip a Context that only has entries" in {
      val context = Context.of(BinaryPropagationSpec.StringKey, "string-value", BinaryPropagationSpec.IntegerKey, 42)
      val writer = inspectableByteStreamWriter()
      binaryPropagation.write(context, writer)

      val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
      rtContext.tags.get(plain("upstream.name")) shouldBe "kamon-application"
      rtContext.get(BinaryPropagationSpec.StringKey) shouldBe "string-value"
      rtContext.get(BinaryPropagationSpec.IntegerKey) shouldBe 0 // there is no entry configuration for the integer key
    }

    "round trip a Context that with tags and entries" in {
      val context = Context.of(TagSet.from(Map("hello" -> "world", "kamon" -> "rulez")))
        .withEntry(BinaryPropagationSpec.StringKey, "string-value")
        .withEntry(BinaryPropagationSpec.IntegerKey, 42)

      val writer = inspectableByteStreamWriter()
      binaryPropagation.write(context, writer)
      val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))

      rtContext.tags.get(plain("hello")) shouldBe "world"
      rtContext.tags.get(plain("kamon")) shouldBe "rulez"
      rtContext.get(BinaryPropagationSpec.StringKey) shouldBe "string-value"
      rtContext.get(BinaryPropagationSpec.IntegerKey) shouldBe 0 // there is no entry configuration for the integer key
    }
  }

  val binaryPropagation = BinaryPropagation.from(
    ConfigFactory.parseString(
      """
        |max-outgoing-size = 128
        |tags.include-upstream-name = yes
        |entries.incoming.string = "kamon.context.BinaryPropagationSpec$StringEntryCodec"
        |entries.incoming.failString = "kamon.context.BinaryPropagationSpec$FailStringEntryCodec"
        |entries.outgoing.string = "kamon.context.BinaryPropagationSpec$StringEntryCodec"
        |entries.outgoing.failString = "kamon.context.BinaryPropagationSpec$FailStringEntryCodec"
        |
      """.stripMargin
    ).withFallback(ConfigFactory.load().getConfig("kamon.propagation"))
  )

  def inspectableByteStreamWriter() = new ByteArrayOutputStream(32) with ByteStreamWriter

}

object BinaryPropagationSpec {

  val StringKey = Context.key[String]("string", null)
  val FailStringKey = Context.key[String]("failString", null)
  val IntegerKey = Context.key[Int]("integer", 0)

  class StringEntryCodec extends EntryReader[ByteStreamReader] with EntryWriter[ByteStreamWriter] {

    override def read(medium: ByteStreamReader, context: Context): Context = {
      val valueData = medium.readAll()

      if (valueData.length > 0) {
        context.withEntry(StringKey, new String(valueData))
      } else context
    }

    override def write(context: Context, medium: ByteStreamWriter): Unit = {
      val value = context.get(StringKey)
      if (value != null) {
        medium.write(value.getBytes)
      }
    }
  }

  class FailStringEntryCodec extends EntryReader[ByteStreamReader] with EntryWriter[ByteStreamWriter] {

    override def read(medium: ByteStreamReader, context: Context): Context = {
      val valueData = medium.readAll()

      if (valueData.length > 0) {
        val stringValue = new String(valueData)
        if (stringValue == "fail-read") {
          sys.error("The fail string entry reader has triggered")
        }

        context.withEntry(FailStringKey, stringValue)
      } else context
    }

    override def write(context: Context, medium: ByteStreamWriter): Unit = {
      val value = context.get(FailStringKey)
      if (value != null && value != "fail-write") {
        medium.write(value.getBytes)
      } else {
        medium.write(42) // malformed data on purpose
        sys.error("The fail string entry writer has triggered")
      }
    }
  }
}
