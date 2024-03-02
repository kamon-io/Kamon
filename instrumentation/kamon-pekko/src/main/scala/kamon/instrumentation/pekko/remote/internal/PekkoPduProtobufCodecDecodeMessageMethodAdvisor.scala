package org.apache.pekko.kamon.instrumentation.pekko.remote.internal

import org.apache.pekko.actor.Address
import pekko.remote.ContextAwareWireFormats_Pekko.AckAndContextAwareEnvelopeContainer
import org.apache.pekko.remote.RemoteActorRefProvider
import org.apache.pekko.util.ByteString
import kamon.Kamon
import kamon.context.BinaryPropagation.ByteStreamReader
import kamon.instrumentation.pekko.PekkoRemoteMetrics
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodEnter}

import scala.annotation.static

/**
  * Advisor for org.apache.pekko.remote.transport.PekkoPduProtobufCodec$::decodeMessage
  */
class PekkoPduProtobufCodecDecodeMessage

object PekkoPduProtobufCodecDecodeMessage {

  @OnMethodEnter
  @static def enter(
    @Argument(0) bs: ByteString,
    @Argument(1) provider: RemoteActorRefProvider,
    @Argument(2) localAddress: Address
  ): Unit = {
    val ackAndEnvelope = AckAndContextAwareEnvelopeContainer.parseFrom(bs.toArray)
    if (ackAndEnvelope.hasEnvelope && ackAndEnvelope.getEnvelope.hasTraceContext) {
      val remoteCtx = ackAndEnvelope.getEnvelope.getTraceContext

      if (remoteCtx.getContext.size() > 0) {
        val ctx = Kamon.defaultBinaryPropagation().read(ByteStreamReader.of(remoteCtx.getContext.toByteArray))
        Kamon.storeContext(ctx)
      }

      val messageSize = ackAndEnvelope.getEnvelope.getMessage.getMessage.size()
      PekkoRemoteMetrics.serializationInstruments(localAddress.system).inboundMessageSize.record(messageSize)
    }
  }
}
