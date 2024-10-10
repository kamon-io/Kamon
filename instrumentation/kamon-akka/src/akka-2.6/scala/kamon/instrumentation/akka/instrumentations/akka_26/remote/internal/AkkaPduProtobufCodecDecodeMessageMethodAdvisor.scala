package akka.kamon.instrumentation.akka.instrumentations.akka_26.remote.internal

import akka.actor.Address
import akka.remote.ContextAwareWireFormats_Akka26.AckAndContextAwareEnvelopeContainer
import akka.remote.RemoteActorRefProvider
import akka.util.ByteString
import kamon.Kamon
import kamon.context.BinaryPropagation.ByteStreamReader
import kamon.instrumentation.akka.AkkaRemoteMetrics
import kanela.agent.libs.net.bytebuddy.asm.Advice.{Argument, OnMethodEnter}

import scala.annotation.static

/**
  * Advisor for akka.remote.transport.AkkaPduProtobufCodec$::decodeMessage
  */
class AkkaPduProtobufCodecDecodeMessage

object AkkaPduProtobufCodecDecodeMessage {

  @OnMethodEnter
  @static def enter(@Argument(0) bs: ByteString, @Argument(1) provider: RemoteActorRefProvider, @Argument(2) localAddress: Address): Unit = {
    val ackAndEnvelope = AckAndContextAwareEnvelopeContainer.parseFrom(bs.toArrayUnsafe())
    if (ackAndEnvelope.hasEnvelope && ackAndEnvelope.getEnvelope.hasTraceContext) {
      val remoteCtx = ackAndEnvelope.getEnvelope.getTraceContext

      if(remoteCtx.getContext.size() > 0) {
        val ctx = Kamon.defaultBinaryPropagation().read(ByteStreamReader.of(remoteCtx.getContext.toByteArray))
        Kamon.storeContext(ctx)
      }

      val messageSize = ackAndEnvelope.getEnvelope.getMessage.getMessage.size()
      AkkaRemoteMetrics.serializationInstruments(localAddress.system).inboundMessageSize.record(messageSize)
    }
  }
}
