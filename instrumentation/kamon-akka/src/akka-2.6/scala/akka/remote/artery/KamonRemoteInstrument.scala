package akka.remote.artery

import akka.actor.{ActorRef, ExtendedActorSystem}
import kamon.Kamon
import kamon.context.BinaryPropagation.{ByteStreamReader, ByteStreamWriter}
import kamon.instrumentation.akka.AkkaRemoteMetrics
import kamon.instrumentation.context.HasContext
import kanela.agent.libs.net.bytebuddy.asm.Advice
import org.slf4j.LoggerFactory

import java.nio.ByteBuffer
import scala.annotation.static
import scala.util.control.NonFatal

class KamonRemoteInstrument(system: ExtendedActorSystem) extends RemoteInstrument {
  private val logger = LoggerFactory.getLogger(classOf[KamonRemoteInstrument])
  private val lengthMask: Int = ~(31 << 26)
  private val serializationInstruments = AkkaRemoteMetrics.serializationInstruments(system.name)

  override def identifier: Byte = 8

  override def serializationTimingEnabled: Boolean = true

  override def remoteWriteMetadata(recipient: ActorRef, message: Object, sender: ActorRef, buffer: ByteBuffer): Unit = {
    val currentContext = Kamon.currentContext()
    if (currentContext.nonEmpty()) {
      Kamon.defaultBinaryPropagation().write(currentContext, ByteStreamWriter.of(buffer))
    }
  }

  override def remoteReadMetadata(recipient: ActorRef, message: Object, sender: ActorRef, buffer: ByteBuffer): Unit = {
    def getLength(kl: Int): Int = kl & lengthMask

    try {

      // We need to figure out the length of the incoming Context before passing it to BinaryPropagation and
      // the only way we can do so at this point is to go back a few positions on `buffer` to read the key/length
      // Integer stored by Akka and figure out the length from there.
      val keyLength = buffer.getInt(buffer.position() - 4)
      val contextLength = getLength(keyLength)
      val contextData = Array.ofDim[Byte](contextLength)
      buffer.get(contextData)

      val incomingContext = Kamon.defaultBinaryPropagation().read(ByteStreamReader.of(contextData))

      Option(CaptureCurrentInboundEnvelope.CurrentInboundEnvelope.get())
        .foreach(_.asInstanceOf[HasContext].setContext(incomingContext))

    } catch {
      case NonFatal(t) =>
        logger.warn("Failed to deserialized incoming Context", t)
    }
  }

  override def remoteMessageSent(recipient: ActorRef, message: Object, sender: ActorRef, size: Int, time: Long): Unit = {
    serializationInstruments.outboundMessageSize.record(size)
    serializationInstruments.serializationTime.record(time)
  }

  override def remoteMessageReceived(recipient: ActorRef, message: Object, sender: ActorRef, size: Int, time: Long): Unit = {
    serializationInstruments.inboundMessageSize.record(size)
    serializationInstruments.deserializationTime.record(time)
  }

  /**
    * Creates a new [[ByteStreamWriter]] from a ByteBuffer.
    */
  def of(byteBuffer: ByteBuffer): ByteStreamWriter = new ByteStreamWriter {
    override def write(bytes: Array[Byte]): Unit =
      byteBuffer.put(bytes)

    override def write(bytes: Array[Byte], offset: Int, count: Int): Unit =
      byteBuffer.put(bytes, offset, count)

    override def write(byte: Int): Unit =
      byteBuffer.put(byte.toByte)
  }
}

class CaptureCurrentInboundEnvelope

object CaptureCurrentInboundEnvelope {

  val CurrentInboundEnvelope = new ThreadLocal[InboundEnvelope]() {
    override def initialValue(): InboundEnvelope = null
  }

  @Advice.OnMethodEnter
  @static def enter(@Advice.Argument(0) inboundEnvelope: InboundEnvelope): Unit = {
    CurrentInboundEnvelope.set(inboundEnvelope)
  }

  @Advice.OnMethodExit
  @static def exit(): Unit = {
    CurrentInboundEnvelope.remove()
  }
}


