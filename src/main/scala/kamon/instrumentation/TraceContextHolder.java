package kamon.instrumentation;
import scala.Option;
import kamon.TraceContext;

abstract public class TraceContextHolder implements Runnable {
    public final Option<TraceContext> context;

    public TraceContextHolder() {
        context = TraceContext.current();
    }

    public Option<TraceContext> getContext() {
        return context;
    }


}