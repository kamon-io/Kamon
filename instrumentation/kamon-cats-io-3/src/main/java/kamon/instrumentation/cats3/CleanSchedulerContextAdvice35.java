package kamon.instrumentation.cats3;

import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import scala.Function1;
import scala.runtime.BoxedUnit;
import scala.runtime.Nothing$;
import scala.util.Right;

public class CleanSchedulerContextAdvice35 {
    @Advice.OnMethodEnter
    public static void enter(@Advice.Argument(value = 1, readOnly = false) Function1<Right<Nothing$, BoxedUnit>, BoxedUnit> callback) {
        callback = new CleanSchedulerContextAdvice35.ContextCleaningWrapper(callback, Kamon.currentContext());
    }


    public static class ContextCleaningWrapper implements Function1<Right<Nothing$, BoxedUnit>, BoxedUnit> {
        private final Function1<Right<Nothing$, BoxedUnit>, BoxedUnit> runnable;
        private final Context context;

        public ContextCleaningWrapper(Function1<Right<Nothing$, BoxedUnit>, BoxedUnit> runnable, Context context) {
            this.runnable = runnable;
            this.context = context;
        }

        @Override
        public BoxedUnit apply(Right<Nothing$, BoxedUnit> v1) {
            try (Storage.Scope ignored = Kamon.storeContext(context)) {
                return runnable.apply(v1);
            }
        }
    }
}
