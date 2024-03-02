package kamon.instrumentation.pekko.instrumentations.remote

import org.apache.pekko.actor.ActorSystem
import kamon.Kamon
import kamon.context.{Context, Storage}
import kamon.context.Storage.Scope
import kamon.instrumentation.pekko.PekkoRemoteInstrumentation
import kamon.instrumentation.pekko.PekkoRemoteMetrics.SerializationInstruments
import kamon.instrumentation.pekko.instrumentations.PekkoPrivateAccess
import kamon.instrumentation.context.{CaptureCurrentContextOnExit, HasContext}
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import org.apache.pekko.kamon.instrumentation.pekko.remote.internal.{
  PekkoPduProtobufCodecConstructMessageMethodInterceptor,
  PekkoPduProtobufCodecDecodeMessage
}
import org.apache.pekko.remote.artery.CaptureCurrentInboundEnvelope

import scala.annotation.static

class RemotingInstrumentation extends InstrumentationBuilder {

  /**
      * Send messages might be buffered if they reach the EndpointWriter before it has been initialized and the current
      * Context might be lost after the buffering, so we make sure we capture the context when the Send command was
      * created and then apply it during the EndpointWrite.writeSend method execution (see below).
      */
  onType("org.apache.pekko.remote.EndpointManager$Send")
    .mixin(classOf[HasContext.Mixin])
    .advise(isConstructor, classOf[CaptureCurrentContextOnExit])

  onType("org.apache.pekko.remote.EndpointWriter")
    .advise(method("writeSend"), classOf[WriteSendWithContext])

  /**
      * Reads and writes the Pekko PDU using a modified version of the Protobuf that has an extra field for a Context
      * instance.
      */
  onType("org.apache.pekko.remote.transport.PekkoPduProtobufCodec$")
    .intercept(method("constructMessage"), new PekkoPduProtobufCodecConstructMessageMethodInterceptor())
    .advise(method("decodeMessage"), classOf[PekkoPduProtobufCodecDecodeMessage])

  /**
      * Mixin Serialization Instruments to the Actor System and use them to record the serialization and deserialization
      * time metrics.
      */
  onType("org.apache.pekko.actor.ActorSystemImpl")
    .mixin(classOf[HasSerializationInstruments.Mixin])
    .advise(isConstructor, classOf[InitializeActorSystemAdvice])

  /**
      * Artery
      */
  onType("org.apache.pekko.remote.artery.ReusableOutboundEnvelope")
    .mixin(classOf[HasContext.Mixin])
    .advise(method("copy"), classOf[CopyContextOnReusableEnvelope])

  onType("org.apache.pekko.remote.artery.Association")
    .advise(method("createOutboundEnvelope$1"), classOf[CaptureCurrentContextOnReusableEnvelope])

  onType("org.apache.pekko.remote.artery.RemoteInstruments")
    .advise(method("deserialize"), classOf[CaptureCurrentInboundEnvelope])

  onType("org.apache.pekko.remote.artery.ReusableInboundEnvelope")
    .mixin(classOf[HasContext.Mixin])
    .advise(method("copyForLane"), classOf[CopyContextOnReusableEnvelope])

  onType("org.apache.pekko.remote.artery.MessageDispatcher")
    .advise(method("dispatch"), classOf[ArteryMessageDispatcherAdvice])

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
  @static def exit(@Advice.Enter scope: Object): Unit = {
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
    if (PekkoRemoteInstrumentation.settings().trackSerializationMetrics) System.nanoTime() else 0L
  }

  @Advice.OnMethodExit
  @static def exit(@Advice.Argument(0) system: AnyRef, @Advice.Enter startNanoTime: Long): Unit = {
    if (startNanoTime != 0L) {
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
    if (PekkoRemoteInstrumentation.settings().trackSerializationMetrics) System.nanoTime() else 0L
  }

  @Advice.OnMethodExit
  @static def exit(
    @Advice.Argument(0) system: AnyRef,
    @Advice.Enter startNanoTime: Long,
    @Advice.Return msg: Any
  ): Unit = {

    if (PekkoPrivateAccess.isSystemMessage(msg)) {
      msg match {
        case hc: HasContext if hc.context == null =>
          hc.setContext(Kamon.currentContext())
        case _ =>
      }
    }

    if (startNanoTime != 0L) {
      system.asInstanceOf[HasSerializationInstruments]
        .serializationInstruments
        .deserializationTime
        .record(System.nanoTime() - startNanoTime)
    }
  }
}
