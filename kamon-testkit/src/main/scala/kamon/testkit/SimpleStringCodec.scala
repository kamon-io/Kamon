package kamon.testkit

import java.nio.ByteBuffer

import kamon.context.{Codecs, Context, TextMap}

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
    val emptyBuffer: ByteBuffer = ByteBuffer.allocate(0)

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
