package kamon.instrumentation;

import kamon.TraceContext;

privileged public aspect PromiseCompletingRunnableInstrumentation {

    declare parents : Future.PromiseCompletingRunnable extends TraceContextHolder;

    pointcut run(scala.concurrent.impl.Future.PromiseCompletingRunnable runnable)
            : execution(* scala.concurrent.impl.Future.PromiseCompletingRunnable.run()) && this(runnable);

    void around(Object runnable)
        : run(runnable) {

        TraceContextHolder contextHolder = (TraceContextHolder) runnable;

        if(contextHolder.context().isDefined()) {
            TraceContext.set(contextHolder.context().get());
            proceed(contextHolder);
            TraceContext.clear();

        } else {
            proceed(contextHolder);
        }
    }
}
