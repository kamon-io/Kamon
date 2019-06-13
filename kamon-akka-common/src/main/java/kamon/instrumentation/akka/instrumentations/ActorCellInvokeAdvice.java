package kamon.instrumentation.akka.instrumentations;

import akka.actor.Cell;
import akka.dispatch.Envelope;
import kamon.instrumentation.akka.instrumentations.HasActorMonitor;
import kamon.context.Context;
import kamon.instrumentation.context.HasContext;
import kamon.instrumentation.context.HasTimestamp;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

final public class ActorCellInvokeAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void enter(
      @Advice.This Cell cell,
      @Advice.Argument(0) Object envelope,
      @Advice.Local("stateFromStart") Object stateFromStart,
      @Advice.Local("processingStartTimestamp") Long processingStartTimestamp,
      @Advice.Local("envelopeTimestamp") Long envelopeTimestamp,
      @Advice.Local("context") Context context) {

    final ActorMonitor actorMonitor = ((HasActorMonitor) cell).actorMonitor();

    processingStartTimestamp = actorMonitor.captureProcessingStartTimestamp();
    context = ((HasContext) envelope).context();
    envelopeTimestamp = ((HasTimestamp) envelope).timestamp();
    stateFromStart = actorMonitor.onMessageProcessingStart(context,  envelopeTimestamp, (Envelope) envelope);
  }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(
        @Advice.This Cell cell,
        @Advice.Local("stateFromStart") Object stateFromStart,
        @Advice.Local("processingStartTimestamp") Long processingStartTimestamp,
        @Advice.Local("envelopeTimestamp") Long envelopeTimestamp,
        @Advice.Local("context") Context context) {

      final ActorMonitor actorMonitor = ((HasActorMonitor) cell).actorMonitor();
      actorMonitor.onMessageProcessingEnd(context, envelopeTimestamp, processingStartTimestamp, stateFromStart);
    }

}