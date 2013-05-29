package kamon.instrumentation;

import kamon.TraceContext;
import scala.concurrent.impl.Future;
import scala.Option;

privileged public aspect PromiseCompletingRunnableInstrumentation {

    declare parents : Future.PromiseCompletingRunnable extends TraceContextHolder;


    pointcut run(Future.PromiseCompletingRunnable runnable)
            : execution(* scala.concurrent.impl.Future.PromiseCompletingRunnable.run()) && this(runnable);

    void around(TraceContextHolder runnable)
        : run(runnable) {

        if(runnable.getContext().isDefined()) {
            System.out.println("########################################################3 There is some context");
            TraceContext.set(runnable.getContext().get());
            proceed(runnable);
            TraceContext.clear();

        } else {
            System.out.println("########################################################3 There is NOOOOO context");
            proceed(runnable);
        }
    }
}
