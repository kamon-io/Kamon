package akka.kamon.instrumentation.kanela.interceptor

import java.io.ByteArrayOutputStream

import akka.KamonOptionVal.OptionVal
import akka.actor.{ActorRef, Address}
import akka.remote.ContextAwareWireFormats.{AckAndContextAwareEnvelopeContainer, ContextAwareRemoteEnvelope, RemoteContext}
import akka.remote.WireFormats.{AcknowledgementInfo, ActorRefData, AddressData, SerializedMessage}
import akka.remote.{Ack, SeqNo}
import akka.util.ByteString
import kamon.Kamon
import kamon.context.BinaryPropagation.ByteStreamWriter
import kamon.instrumentation.akka.AkkaRemoteMetrics
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{Argument, RuntimeType}

/**
  * Interceptor for akka.remote.transport.AkkaPduProtobufCodec$::constructMessage
  */
class AkkaPduProtobufCodecConstructMessageMethodInterceptor {

  @RuntimeType
  def aroundConstructMessage(@Argument(0) localAddress: Address,
                             @Argument(1) recipient: ActorRef,
                             @Argument(2) serializedMessage: SerializedMessage,
                             @Argument(3) senderOption: OptionVal[ActorRef],
                             @Argument(4) seqOption: Option[SeqNo],
                             @Argument(5) ackOption: Option[Ack]): AnyRef = {

    val ackAndEnvelopeBuilder = AckAndContextAwareEnvelopeContainer.newBuilder
    val envelopeBuilder = ContextAwareRemoteEnvelope.newBuilder

    envelopeBuilder.setRecipient(serializeActorRef(recipient.path.address, recipient))
    if (senderOption.isDefined)
      envelopeBuilder.setSender(serializeActorRef(localAddress, senderOption.get))
    seqOption foreach { seq => envelopeBuilder.setSeq(seq.rawValue) }
    ackOption foreach { ack => ackAndEnvelopeBuilder.setAck(ackBuilder(ack)) }
    envelopeBuilder.setMessage(serializedMessage)

    val out = new ByteArrayOutputStream()
    Kamon.defaultBinaryPropagation().write(Kamon.currentContext(), ByteStreamWriter.of(out))

    val remoteTraceContext = RemoteContext.newBuilder().setContext(
      akka.protobuf.ByteString.copyFrom(out.toByteArray)
    )
    envelopeBuilder.setTraceContext(remoteTraceContext)

    ackAndEnvelopeBuilder.setEnvelope(envelopeBuilder)

    val messageSize = envelopeBuilder.getMessage.getMessage.size()
    AkkaRemoteMetrics.serializationInstruments(localAddress.system).outboundMessageSize.record(messageSize)

    ByteString.ByteString1C(ackAndEnvelopeBuilder.build.toByteArray) //Reuse Byte Array (naughty!)
  }

  // Copied from akka.remote.transport.AkkaPduProtobufCodec because of private access.
  private def ackBuilder(ack: Ack): AcknowledgementInfo.Builder = {
    val ackBuilder = AcknowledgementInfo.newBuilder()
    ackBuilder.setCumulativeAck(ack.cumulativeAck.rawValue)
    ack.nacks foreach { nack => ackBuilder.addNacks(nack.rawValue) }
    ackBuilder
  }

  // Copied from akka.remote.transport.AkkaPduProtobufCodec because of private access.
  private def serializeActorRef(defaultAddress: Address, ref: ActorRef): ActorRefData = {
    ActorRefData.newBuilder.setPath(
      if (ref.path.address.host.isDefined) ref.path.toSerializationFormat
      else ref.path.toSerializationFormatWithAddress(defaultAddress)).build()
  }

  // Copied from akka.remote.transport.AkkaPduProtobufCodec because of private access.
  private def serializeAddress(address: Address): AddressData = address match {
    case Address(protocol, system, Some(host), Some(port)) =>
      AddressData.newBuilder
        .setHostname(host)
        .setPort(port)
        .setSystem(system)
        .setProtocol(protocol)
        .build()
    case _ => throw new IllegalArgumentException(s"Address [$address] could not be serialized: host or port missing.")
  }
}