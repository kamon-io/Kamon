package kamon.instrumentation.akka.instrumentations;

import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class SchedulerRunnableAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void enter(@Advice.Argument(value = 1, readOnly = false) Runnable runnable) {
    runnable = new ContextAwareRunnable(Kamon.currentContext(), runnable);
  }

  public static class ContextAwareRunnable implements Runnable {
    private final Context context;
    private final Runnable underlyingRunnable;

    public ContextAwareRunnable(Context context, Runnable underlyingRunnable) {
      this.context = context;
      this.underlyingRunnable = underlyingRunnable;
    }

    @Override
    public void run() {
      final Storage.Scope scope = Kamon.storeContext(context);

      try {
        underlyingRunnable.run();
      } finally {
        scope.close();
      }
    }
  }
}
