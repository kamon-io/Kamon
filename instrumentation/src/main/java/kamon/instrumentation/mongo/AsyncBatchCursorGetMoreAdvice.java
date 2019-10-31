package kamon.instrumentation.mongo;

import com.mongodb.MongoNamespace;
import com.mongodb.async.SingleResultCallback;
import kamon.instrumentation.context.HasContext;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

import java.util.List;

public class AsyncBatchCursorGetMoreAdvice {

  @Advice.OnMethodEnter
  public static <T> void enter(
      @Advice.This Object batchCursor,
      @Advice.FieldValue("namespace") MongoNamespace namespace,
      @Advice.Argument(value = 2, readOnly = false, optional = true) SingleResultCallback<T> callback) {

    final Span parentSpan = ((HasContext) batchCursor).context().get(Span.Key());
    final Span getMoreSpan = MongoClientInstrumentation.getMoreSpanBuilder(parentSpan, namespace).start();
    callback = spanCompletingCallback(callback, getMoreSpan);
  }

  public static <T> SingleResultCallback<T> spanCompletingCallback(SingleResultCallback<T> originalCallback, Span span) {
    return new SingleResultCallback<T>() {

      @Override
      public void onResult(T result, Throwable t) {
        try {
          if(result != null) {
            span.finish();
          } else {
            span.fail(t).finish();
          }

        } finally {
          originalCallback.onResult(result, t);
        }
      }
    };
  }
}
