package akka.kamon.instrumentation.kanela.advisor;

import akka.actor.Cell;
import akka.dispatch.Envelope;
import akka.kamon.instrumentation.ActorInstrumentationAware;
import akka.kamon.instrumentation.ActorMonitor;
import akka.kamon.instrumentation.InstrumentedEnvelope;
import akka.kamon.instrumentation.TimestampedContext;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class ActorCellInvokeAdvisor {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This Cell cell,
                               @Advice.Argument(0) Object envelope,
                               @Advice.Local("toClose") Object toClose,
                               @Advice.Local("timestampBeforeProcessing") Long timestampBeforeProcessing,
                               @Advice.Local("timestampedContext") TimestampedContext timestampedContext) {
        ActorMonitor instrumentation = ((ActorInstrumentationAware) cell).actorInstrumentation();
        timestampBeforeProcessing = instrumentation.processMessageStartTimestamp();
        timestampedContext = ((InstrumentedEnvelope) envelope).timestampedContext();
        toClose = instrumentation.processMessageStart(timestampedContext, (Envelope) envelope);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This Cell cell,
                              @Advice.Local("toClose") Object toClose,
                              @Advice.Local("timestampBeforeProcessing") Long timestampBeforeProcessing,
                              @Advice.Local("timestampedContext") TimestampedContext timestampedContext) {
        ActorMonitor instrumentation = ((ActorInstrumentationAware) cell).actorInstrumentation();
        instrumentation.processMessageEnd(timestampedContext, toClose, timestampBeforeProcessing);
    }

}