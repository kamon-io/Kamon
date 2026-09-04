package kamon.instrumentation.lettuce;

import kamon.trace.Span;

import java.util.function.BiFunction;

public class AsyncCommandCallback<T, U extends Throwable, R>
        implements BiFunction<T, Throwable, R> {

    private final Span span;

    public AsyncCommandCallback(final Span span) {
        this.span = span;
    }

    @Override
    public R apply(final T t, final Throwable throwable) {
        if (throwable != null) {
            span.fail(throwable);
        }
        span.finish();
        return null;
    }
}
