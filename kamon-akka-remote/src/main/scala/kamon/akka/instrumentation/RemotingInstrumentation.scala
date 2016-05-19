package akka.kamon.instrumentation

import akka.actor.{ ActorRef, Address }
import akka.remote.WireFormats._
import akka.remote.instrumentation.TraceContextAwareWireFormats.{ AckAndTraceContextAwareEnvelopeContainer, RemoteTraceContext, TraceContextAwareRemoteEnvelope }
import akka.remote.{ Ack, RemoteActorRefProvider, SeqNo }
import akka.util.ByteString
import kamon.Kamon
import kamon.trace.{ States, Tracer }
import kamon.util.MilliTimestamp
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class RemotingInstrumentation {

  @Pointcut("execution(* akka.remote.transport.AkkaPduProtobufCodec$.constructMessage(..)) && " +
    "args(localAddress, recipient, serializedMessage, senderOption, seqOption, ackOption)")
  def constructAkkaPduMessage(localAddress: Address, recipient: ActorRef, serializedMessage: SerializedMessage,
    senderOption: Option[ActorRef], seqOption: Option[SeqNo], ackOption: Option[Ack]): Unit = {}

  @Around("constructAkkaPduMessage(localAddress, recipient, serializedMessage, senderOption, seqOption, ackOption)")
  def aroundSerializeRemoteMessage(pjp: ProceedingJoinPoint, localAddress: Address, recipient: ActorRef,
    serializedMessage: SerializedMessage, senderOption: Option[ActorRef], seqOption: Option[SeqNo], ackOption: Option[Ack]): AnyRef = {

    val ackAndEnvelopeBuilder = AckAndTraceContextAwareEnvelopeContainer.newBuilder
    val envelopeBuilder = TraceContextAwareRemoteEnvelope.newBuilder

    envelopeBuilder.setRecipient(serializeActorRef(recipient.path.address, recipient))
    senderOption foreach { ref ⇒ envelopeBuilder.setSender(serializeActorRef(localAddress, ref)) }
    seqOption foreach { seq ⇒ envelopeBuilder.setSeq(seq.rawValue) }
    ackOption foreach { ack ⇒ ackAndEnvelopeBuilder.setAck(ackBuilder(ack)) }
    envelopeBuilder.setMessage(serializedMessage)

    // Attach the TraceContext info, if available.
    Tracer.currentContext.collect { context ⇒

      envelopeBuilder.setTraceContext(RemoteTraceContext.newBuilder()
        .setTraceName(context.name)
        .setTraceToken(context.token)
        .setIsOpen(States.Open == context.status)
        .setStartMilliTime(context.startTimestamp.toMilliTimestamp.millis)
        .build())
    }

    ackAndEnvelopeBuilder.setEnvelope(envelopeBuilder)
    ByteString.ByteString1C(ackAndEnvelopeBuilder.build.toByteArray) //Reuse Byte Array (naughty!)
  }

  // Copied from akka.remote.transport.AkkaPduProtobufCodec because of private access.
  private def ackBuilder(ack: Ack): AcknowledgementInfo.Builder = {
    val ackBuilder = AcknowledgementInfo.newBuilder()
    ackBuilder.setCumulativeAck(ack.cumulativeAck.rawValue)
    ack.nacks foreach { nack ⇒ ackBuilder.addNacks(nack.rawValue) }
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
    case Address(protocol, system, Some(host), Some(port)) ⇒
      AddressData.newBuilder
        .setHostname(host)
        .setPort(port)
        .setSystem(system)
        .setProtocol(protocol)
        .build()
    case _ ⇒ throw new IllegalArgumentException(s"Address [$address] could not be serialized: host or port missing.")
  }

  @Pointcut("execution(* akka.remote.transport.AkkaPduProtobufCodec$.decodeMessage(..)) && args(bs, provider, localAddress)")
  def decodeRemoteMessage(bs: ByteString, provider: RemoteActorRefProvider, localAddress: Address): Unit = {}

  @Around("decodeRemoteMessage(bs, provider, localAddress)")
  def aroundDecodeRemoteMessage(pjp: ProceedingJoinPoint, bs: ByteString, provider: RemoteActorRefProvider, localAddress: Address): AnyRef = {
    val ackAndEnvelope = AckAndTraceContextAwareEnvelopeContainer.parseFrom(bs.toArray)

    if (ackAndEnvelope.hasEnvelope && ackAndEnvelope.getEnvelope.hasTraceContext) {
      val remoteTraceContext = ackAndEnvelope.getEnvelope.getTraceContext
      val system = provider.guardian.underlying.system
      val tracer = Kamon.tracer

      val ctx = tracer.newContext(
        remoteTraceContext.getTraceName,
        Option(remoteTraceContext.getTraceToken),
        tags = Map.empty,
        new MilliTimestamp(remoteTraceContext.getStartMilliTime).toRelativeNanoTimestamp,
        if (remoteTraceContext.getIsOpen) States.Open else States.Closed,
        isLocal = false)

      Tracer.setCurrentContext(ctx)
    }

    pjp.proceed()
  }
}
