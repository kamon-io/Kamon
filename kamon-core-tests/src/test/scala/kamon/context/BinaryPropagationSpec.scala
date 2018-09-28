package kamon.context

import java.io.ByteArrayOutputStream

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.BinaryPropagation.{ByteStreamReader, ByteStreamWriter}
import kamon.context.Propagation.{EntryReader, EntryWriter}
import org.scalatest.{Matchers, OptionValues, WordSpec}

class BinaryPropagationSpec extends WordSpec with Matchers with OptionValues {

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

    "round trip a Context that only has tags" in {
      val context = Context.of(Map("hello" -> "world", "kamon" -> "rulez"))
      val writer = inspectableByteStreamWriter()
      binaryPropagation.write(context, writer)

      val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
      rtContext.entries shouldBe empty
      rtContext.tags should contain theSameElementsAs (context.tags)
    }

    "round trip a Context that only has entries" in {
      val context = Context.of(BinaryPropagationSpec.StringKey, "string-value", BinaryPropagationSpec.IntegerKey, 42)
      val writer = inspectableByteStreamWriter()
      binaryPropagation.write(context, writer)

      val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
      rtContext.tags shouldBe empty
      rtContext.get(BinaryPropagationSpec.StringKey) shouldBe "string-value"
      rtContext.get(BinaryPropagationSpec.IntegerKey) shouldBe 0 // there is no entry configuration for the integer key
    }

    "round trip a Context that with tags and entries" in {
      val context = Context.of(Map("hello" -> "world", "kamon" -> "rulez"))
        .withKey(BinaryPropagationSpec.StringKey, "string-value")
        .withKey(BinaryPropagationSpec.IntegerKey, 42)

      val writer = inspectableByteStreamWriter()
      binaryPropagation.write(context, writer)
      val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))

      rtContext.tags should contain theSameElementsAs (context.tags)
      rtContext.get(BinaryPropagationSpec.StringKey) shouldBe "string-value"
      rtContext.get(BinaryPropagationSpec.IntegerKey) shouldBe 0 // there is no entry configuration for the integer key
    }
  }

  val binaryPropagation = BinaryPropagation.from(
    ConfigFactory.parseString(
      """
        |
        |entries.incoming.string = "kamon.context.BinaryPropagationSpec$StringEntryCodec"
        |entries.outgoing.string = "kamon.context.BinaryPropagationSpec$StringEntryCodec"
        |
      """.stripMargin
    ).withFallback(ConfigFactory.load().getConfig("kamon.propagation")), Kamon)


  def inspectableByteStreamWriter() = new ByteArrayOutputStream(32) with ByteStreamWriter

}

object BinaryPropagationSpec {

  val StringKey = Context.key[String]("string", null)
  val IntegerKey = Context.key[Int]("integer", 0)

  class StringEntryCodec extends EntryReader[ByteStreamReader] with EntryWriter[ByteStreamWriter] {

    override def read(medium: ByteStreamReader, context: Context): Context = {
      val valueData = medium.readAll()

      if(valueData.length > 0) {
        context.withKey(StringKey, new String(valueData))
      } else context
    }

    override def write(context: Context, medium: ByteStreamWriter): Unit = {
      val value = context.get(StringKey)
      if(value != null) {
        medium.write(value.getBytes)
      }
    }
  }
}