package kamon.instrumentation.zio2;

import kanela.agent.libs.net.bytebuddy.asm.Advice;
import zio.Supervisor;

public class SupervisorAdvice {

    public static class OverrideDefaultSupervisor {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.Return(readOnly = false) Supervisor<?> supervisor) {
            supervisor = supervisor.$plus$plus(new NewSupervisor());
        }
    }
}
