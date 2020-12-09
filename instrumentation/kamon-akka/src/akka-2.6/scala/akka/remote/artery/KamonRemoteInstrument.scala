package akka.remote.artery

// do i really need this package?

import akka.actor.ActorRef
import kamon.Kamon
import kamon.context.BinaryPropagation.ByteStreamReader

import java.nio.ByteBuffer

class KamonRemoteInstrument extends RemoteInstrument {
  // identifier? anything between 8 and 32 is ok
  override def identifier: Byte = 19

  override def remoteWriteMetadata(recipient: ActorRef, message: Object, sender: ActorRef, buffer: ByteBuffer): Unit = {
    Kamon.defaultBinaryPropagation()
      .storeContextInBuffer(Kamon.currentContext(), buffer)
    ()
  }

  override def remoteMessageSent(recipient: ActorRef, message: Object, sender: ActorRef, size: Int, time: Long): Unit = {
    ()
  }

  override def remoteReadMetadata(recipient: ActorRef, message: Object, sender: ActorRef, buffer: ByteBuffer): Unit = {
    val contextSize = buffer.getInt()
    val reader = ByteStreamReader.of(buffer.array().slice(
      buffer.position(),
      buffer.position() + contextSize))
    val context = Kamon.defaultBinaryPropagation().read(reader)
    Kamon.storeContext(context)
    buffer.position(buffer.position() + contextSize)
    ()
  }

  override def remoteMessageReceived(recipient: ActorRef, message: Object, sender: ActorRef, size: Int, time: Long): Unit = {
    ()
  }

}

