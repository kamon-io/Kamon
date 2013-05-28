package kamon.instrumentation;

import kamon.TraceContext;
import scala.concurrent.impl.Future;
import scala.Option;

public aspect PromiseCompletingRunnableInstrumentation {

    public final Option<TraceContext> Future.PromiseCompletingRunnable.context = TraceContext.current();

    pointcut run(Future.PromiseCompletingRunnable runnable)
            : execution(* scala.concurrent.impl.Future.PromiseCompletingRunnable.run()) && this(runnable);

    void around(Future.PromiseCompletingRunnable runnable)
        : run(runnable) {

        if(runnable.context.isDefined()) {

            TraceContext.set((TraceContext) runnable.context.get());
            proceed(runnable);
            TraceContext.clear();

        } else {
            proceed(runnable);
        }


    }
}
