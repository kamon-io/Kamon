package kamon.util;

import kamon.trace.Span;

import java.util.concurrent.CompletionStage;

/**
 * Tiny helper to bypass Scala type issues when trying to create a BiFunction for CompletionStage.handle(...).
 */
public class CompletionStageSpanFinisher {

    public static CompletionStage<?> finishWhenDone(CompletionStage<?> cs, Span span) {
        return cs.handle((a, t) -> {
            if(t != null)
                span.fail(t);
            span.finish();

            return cs;
        });
    }
}
