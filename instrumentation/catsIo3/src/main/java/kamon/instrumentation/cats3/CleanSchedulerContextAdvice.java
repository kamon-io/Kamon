package kamon.instrumentation.cats3;

import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class CleanSchedulerContextAdvice {

  @Advice.OnMethodEnter
  public static void enter(@Advice.Argument(value = 1, readOnly = false) Runnable runnable) {
    runnable = new ContextCleaningWrapper(runnable, Kamon.currentContext());
  }


  public static class ContextCleaningWrapper implements Runnable {
    private final Runnable runnable;
    private final Context context;

    public ContextCleaningWrapper(Runnable runnable, Context context) {
      this.runnable = runnable;
      this.context = context;
    }

    @Override
    public void run() {
      try (Storage.Scope ignored = Kamon.storeContext(context)) {
        runnable.run();
      }
    }
  }
}
