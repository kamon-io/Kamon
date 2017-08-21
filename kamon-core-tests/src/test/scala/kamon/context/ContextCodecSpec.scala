package kamon.context

import java.nio.ByteBuffer

import kamon.Kamon
import kamon.testkit.ContextTesting
import org.scalatest.{Matchers, OptionValues, WordSpec}

class ContextCodecSpec extends WordSpec with Matchers with ContextTesting with OptionValues {
  "the Context Codec" when {
    "encoding/decoding to HttpHeaders" should {
      "round trip a empty context" in {
        val textMap = ContextCodec.HttpHeaders.encode(Context.Empty)
        val decodedContext = ContextCodec.HttpHeaders.decode(textMap)

        decodedContext shouldBe Context.Empty
      }

      "round trip a context with only local keys" in {
        val localOnlyContext = Context.create(StringKey, Some("string-value"))
        val textMap = ContextCodec.HttpHeaders.encode(localOnlyContext)
        val decodedContext = ContextCodec.HttpHeaders.decode(textMap)

        decodedContext shouldBe Context.Empty
      }

      "round trip a context with local and broadcast keys" in {
        val initialContext = Context.create()
          .withKey(StringKey, Some("string-value"))
          .withKey(StringBroadcastKey, Some("this-should-be-round-tripped"))

        val textMap = ContextCodec.HttpHeaders.encode(initialContext)
        val decodedContext = ContextCodec.HttpHeaders.decode(textMap)

        decodedContext.get(StringKey) shouldBe empty
        decodedContext.get(StringBroadcastKey).value shouldBe "this-should-be-round-tripped"
      }
    }

    "encoding/decoding to Binary" should {
      "round trip a empty context" in {
        val byteBuffer = ContextCodec.Binary.encode(Context.Empty)

        val decodedContext = ContextCodec.Binary.decode(byteBuffer)

        decodedContext shouldBe Context.Empty
      }

      "round trip a context with only local keys" in {
        val localOnlyContext = Context.create(StringKey, Some("string-value"))
        val byteBuffer = ContextCodec.Binary.encode(localOnlyContext)
        val decodedContext = ContextCodec.Binary.decode(byteBuffer)

        decodedContext shouldBe Context.Empty
      }

      "round trip a context with local and broadcast keys" in {
        val initialContext = Context.create()
          .withKey(StringKey, Some("string-value"))
          .withKey(StringBroadcastKey, Some("this-should-be-round-tripped"))

        val byteBuffer = ContextCodec.Binary.encode(initialContext)
        val decodedContext = ContextCodec.Binary.decode(byteBuffer)

        decodedContext.get(StringKey) shouldBe empty
        decodedContext.get(StringBroadcastKey).value shouldBe "this-should-be-round-tripped"
      }
    }
  }

  val ContextCodec = new Codecs(Kamon.config())
}

object SimpleStringCodec {
  final class Headers extends Codecs.ForEntry[TextMap] {
    private val dataKey = "X-String-Value"

    override def encode(context: Context): TextMap = {
      val textMap = TextMap.Default()
      context.get(ContextTesting.StringBroadcastKey).foreach { value =>
        textMap.put(dataKey, value)
      }

      textMap
    }

    override def decode(carrier: TextMap, context: Context): Context = {
      carrier.get(dataKey) match {
        case value @ Some(_) => context.withKey(ContextTesting.StringBroadcastKey, value)
        case None            => context
      }
    }
  }

  final class Binary extends Codecs.ForEntry[ByteBuffer] {
    val emptyBuffer = ByteBuffer.allocate(0)

    override def encode(context: Context): ByteBuffer = {
      context.get(ContextTesting.StringBroadcastKey) match {
        case Some(value)  => ByteBuffer.wrap(value.getBytes)
        case None         => emptyBuffer
      }
    }

    override def decode(carrier: ByteBuffer, context: Context): Context = {
      context.withKey(ContextTesting.StringBroadcastKey, Some(new String(carrier.array())))
    }
  }

}
