package org.apache.pekko.remote.kamon.instrumentation.pekko.remote.internal.remote

import java.nio.ByteBuffer

import org.apache.pekko.remote.artery._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.Serialization
import kamon.Kamon
import kamon.context.{BinaryPropagation, Context}
import kamon.instrumentation.pekko.PekkoRemoteMetrics
import kamon.instrumentation.context.HasContext
import kanela.agent.libs.net.bytebuddy.asm.Advice


/**
  * For Artery messages we will always add two sections to the end of each serialized message: the Context and the size
  * of the Context. The layout will look something like this:
  *
  *   |------------------ Actual Message ------------------||-- Kamon Context --||-- Context Size (4 bytes) --|
  *
  * If the Context is empty the Context size will be zero.
  */

class SerializeForArteryAdvice
object SerializeForArteryAdvice {

  @Advice.OnMethodEnter
  def enter(): Long = {
    System.nanoTime()
  }

  @Advice.OnMethodExit
  def exit(@Advice.Argument(0) serialization: Serialization, @Advice.Argument(1) envelope: OutboundEnvelope,
      @Advice.Argument(3) envelopeBuffer: EnvelopeBuffer, @Advice.Enter startTime: Long): Unit = {

    val instruments = PekkoRemoteMetrics.serializationInstruments(serialization.system.name)
    val messageBuffer = envelopeBuffer.byteBuffer
    val context = envelope.asInstanceOf[HasContext].context
    val positionBeforeContext = messageBuffer.position()

    if(context.nonEmpty()) {
      Kamon.defaultBinaryPropagation().write(context, byteBufferWriter(messageBuffer))
    }

    instruments.serializationTime.record(System.nanoTime() - startTime)
    instruments.outboundMessageSize.record(positionBeforeContext)

    val contextSize = messageBuffer.position() - positionBeforeContext
    messageBuffer.putInt(contextSize)
  }

  def byteBufferWriter(bb: ByteBuffer): BinaryPropagation.ByteStreamWriter = new BinaryPropagation.ByteStreamWriter {
    override def write(bytes: Array[Byte]): Unit =
      bb.put(bytes)

    override def write(bytes: Array[Byte], offset: Int, count: Int): Unit =
      bb.put(bytes, offset, count)

    override def write(byte: Int): Unit =
      bb.put(byte.toByte)
  }
}

class DeserializeForArteryAdvice
object DeserializeForArteryAdvice {

  val LastDeserializedContext = new ThreadLocal[Context]() {
    override def initialValue(): Context = null
  }

  case class DeserializationInfo(
    context: Context,
    timeStamp: Long,
    messageSize: Long
  )

  @Advice.OnMethodEnter
  def exit(@Advice.Argument(5) envelopeBuffer: EnvelopeBuffer): DeserializationInfo = {
    val startTime = System.nanoTime()
    val messageBuffer = envelopeBuffer.byteBuffer
    val messageStart = messageBuffer.position()

    messageBuffer.mark()
    messageBuffer.position(messageBuffer.limit() - 4)
    val contextSize = messageBuffer.getInt()
    val contextStart = messageBuffer.limit() - (contextSize + 4)
    val messageSize = contextStart - messageStart

    val context = if(contextSize == 0)
      Context.Empty
    else {
      messageBuffer
        .position(contextStart)
        .limit(contextStart + contextSize)

      Kamon.defaultBinaryPropagation().read(byteBufferReader(messageBuffer))
    }

    messageBuffer.reset()
    messageBuffer.limit(contextStart)
    DeserializationInfo(context, startTime, messageSize)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable])
  def exit(@Advice.Argument(0) system: ActorSystem, @Advice.Argument(5) envelopeBuffer: EnvelopeBuffer,
      @Advice.Enter deserializationInfo: DeserializationInfo, @Advice.Thrown error: Throwable): Unit = {

    if(error == null) {
      LastDeserializedContext.set(deserializationInfo.context)

      val instruments = PekkoRemoteMetrics.serializationInstruments(system.name)
      instruments.deserializationTime.record(System.nanoTime() - deserializationInfo.timeStamp)
      instruments.inboundMessageSize.record(deserializationInfo.messageSize)
    }
  }


  def byteBufferReader(bb: ByteBuffer): BinaryPropagation.ByteStreamReader = new BinaryPropagation.ByteStreamReader {
    override def available(): Int =
      bb.remaining()

    override def read(target: Array[Byte]): Int = {
      bb.get(target)
      target.length
    }

    override def read(target: Array[Byte], offset: Int, count: Int): Int = {
      bb.get(target, offset, count)
      target.length
    }

    override def readAll(): Array[Byte] = {
      val array = Array.ofDim[Byte](bb.remaining())
      bb.get(array)
      array
    }
  }
}


class CaptureContextOnInboundEnvelope
object CaptureContextOnInboundEnvelope {

  @Advice.OnMethodEnter
  def enter(@Advice.This inboundEnvelope: Any): Unit = {
    val lastContext = DeserializeForArteryAdvice.LastDeserializedContext.get()
    if(lastContext != null) {
      inboundEnvelope.asInstanceOf[HasContext].setContext(lastContext)
      DeserializeForArteryAdvice.LastDeserializedContext.set(null)
    }
  }

}
