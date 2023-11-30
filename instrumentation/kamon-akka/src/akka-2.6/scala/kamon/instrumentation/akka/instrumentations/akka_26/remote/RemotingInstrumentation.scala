package kamon.instrumentation.akka.instrumentations.akka_26.remote

import akka.actor.ActorSystem
import akka.remote.kamon.instrumentation.akka.instrumentations.akka_26.remote.{CaptureContextOnInboundEnvelope, DeserializeForArteryAdvice, SerializeForArteryAdvice}
import akka.kamon.instrumentation.akka.instrumentations.akka_26.remote.internal.{AkkaPduProtobufCodecConstructMessageMethodInterceptor, AkkaPduProtobufCodecDecodeMessage}
import akka.remote.artery.CaptureCurrentInboundEnvelope
import kamon.Kamon
import kamon.context.{Context, Storage}
import kamon.context.Storage.Scope
import kamon.instrumentation.akka.AkkaRemoteInstrumentation
import kamon.instrumentation.akka.AkkaRemoteMetrics.SerializationInstruments
import kamon.instrumentation.akka.instrumentations.{AkkaPrivateAccess, VersionFiltering}
import kamon.instrumentation.context.{CaptureCurrentContextOnExit, HasContext}
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.annotation.static


class RemotingInstrumentation extends InstrumentationBuilder with VersionFiltering {

  onAkka("2.6", "2.7") {

    /**
      * Send messages might be buffered if they reach the EndpointWriter before it has been initialized and the current
      * Context might be lost after the buffering, so we make sure we capture the context when the Send command was
      * created and then apply it during the EndpointWrite.writeSend method execution (see below).
      */
    onType("akka.remote.EndpointManager$Send")
      .mixin(classOf[HasContext.Mixin])
      .advise(isConstructor, CaptureCurrentContextOnExit)

    onType("akka.remote.EndpointWriter")
      .advise(method("writeSend"), WriteSendWithContext)

    /**
      * Reads and writes the Akka PDU using a modified version of the Protobuf that has an extra field for a Context
      * instance.
      */
    onType("akka.remote.transport.AkkaPduProtobufCodec$")
      .intercept(method("constructMessage"), new AkkaPduProtobufCodecConstructMessageMethodInterceptor())
      .advise(method("decodeMessage"), classOf[AkkaPduProtobufCodecDecodeMessage])

    /**
      * Mixin Serialization Instruments to the Actor System and use them to record the serialization and deserialization
      * time metrics.
      */
    onType("akka.actor.ActorSystemImpl")
      .mixin(classOf[HasSerializationInstruments.Mixin])
      .advise(isConstructor, InitializeActorSystemAdvice)

    /**
      * Artery
      */
    onType("akka.remote.artery.ReusableOutboundEnvelope")
      .mixin(classOf[HasContext.Mixin])
      .advise(method("copy"), CopyContextOnReusableEnvelope)

    onType("akka.remote.artery.Association")
      .advise(method("createOutboundEnvelope$1"), CaptureCurrentContextOnReusableEnvelope)

    onType("akka.remote.artery.RemoteInstruments")
      .advise(method("deserialize"), classOf[CaptureCurrentInboundEnvelope])

    onType("akka.remote.artery.ReusableInboundEnvelope")
      .mixin(classOf[HasContext.Mixin])
      .advise(method("copyForLane"), CopyContextOnReusableEnvelope)

    onType("akka.remote.artery.MessageDispatcher")
      .advise(method("dispatch"), ArteryMessageDispatcherAdvice)
  }

}

class ArteryMessageDispatcherAdvice
object ArteryMessageDispatcherAdvice {

  @Advice.OnMethodEnter
  @static def enter(@Advice.Argument(0) envelope: Any): Storage.Scope =
    Kamon.storeContext(envelope.asInstanceOf[HasContext].context)

  @Advice.OnMethodExit
  @static def exit(@Advice.Enter scope: Storage.Scope): Unit =
    scope.close()
}

class CopyContextOnReusableEnvelope
object CopyContextOnReusableEnvelope {

  @Advice.OnMethodExit
  @static def exit(@Advice.This oldEnvelope: Any, @Advice.Return newEnvelope: Any): Unit =
    newEnvelope.asInstanceOf[HasContext].setContext(oldEnvelope.asInstanceOf[HasContext].context)
}

class CaptureCurrentContextOnReusableEnvelope
object CaptureCurrentContextOnReusableEnvelope {

  @Advice.OnMethodExit
  @static def exit(@Advice.Return envelope: Any): Unit = {
    envelope.asInstanceOf[HasContext].setContext(Kamon.currentContext())
  }
}

class WriteSendWithContext
object WriteSendWithContext {

  @Advice.OnMethodEnter
  @static def enter(@Advice.Argument(0) send: Any): Scope = {
    Kamon.storeContext(send.asInstanceOf[HasContext].context)
  }

  @Advice.OnMethodExit
  @static def exit(@Advice.Enter scope: Scope): Unit = {
    scope.asInstanceOf[Scope].close()
  }
}

trait HasSerializationInstruments {
  def serializationInstruments: SerializationInstruments
  def setSerializationInstruments(instruments: SerializationInstruments): Unit
}

object HasSerializationInstruments {

  class Mixin(var serializationInstruments: SerializationInstruments) extends HasSerializationInstruments {
    override def setSerializationInstruments(instruments: SerializationInstruments): Unit =
      serializationInstruments = instruments
  }
}

class InitializeActorSystemAdvice
object InitializeActorSystemAdvice {

  @Advice.OnMethodExit
  @static def exit(@Advice.This system: ActorSystem with HasSerializationInstruments): Unit =
    system.setSerializationInstruments(new SerializationInstruments(system.name))

}

class MeasureSerializationTime
object MeasureSerializationTime {

  @Advice.OnMethodEnter
  @static def enter(): Long = {
    if(AkkaRemoteInstrumentation.settings().trackSerializationMetrics) System.nanoTime() else 0L
  }

  @Advice.OnMethodExit
  @static def exit(@Advice.Argument(0) system: AnyRef, @Advice.Enter startNanoTime: Long): Unit = {
    if(startNanoTime != 0L) {
      system.asInstanceOf[HasSerializationInstruments]
        .serializationInstruments
        .serializationTime
        .record(System.nanoTime() - startNanoTime)
    }
  }
}

class MeasureDeserializationTime
object MeasureDeserializationTime {

  @Advice.OnMethodEnter
  @static def enter(): Long = {
    if(AkkaRemoteInstrumentation.settings().trackSerializationMetrics) System.nanoTime() else 0L
  }

  @Advice.OnMethodExit
  @static def exit(@Advice.Argument(0) system: AnyRef, @Advice.Enter startNanoTime: Long, @Advice.Return msg: Any): Unit = {

    if(AkkaPrivateAccess.isSystemMessage(msg)) {
      msg match {
        case hc: HasContext if hc.context == null =>
          hc.setContext(Kamon.currentContext())
        case _ =>
      }
    }

    if(startNanoTime != 0L) {
      system.asInstanceOf[HasSerializationInstruments]
        .serializationInstruments
        .deserializationTime
        .record(System.nanoTime() - startNanoTime)
    }
  }
}
