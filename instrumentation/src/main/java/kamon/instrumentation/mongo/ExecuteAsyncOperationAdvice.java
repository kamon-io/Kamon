package kamon.instrumentation.mongo;

import com.mongodb.MongoNamespace;
import com.mongodb.async.AsyncBatchCursor;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.operation.BatchCursor;
import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage;
import kamon.instrumentation.context.HasContext;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import kamon.instrumentation.mongo.MongoClientInstrumentation.HasOperationName;

public class ExecuteAsyncOperationAdvice {

  @Advice.OnMethodEnter()
  public static <T> void enter(
      @Advice.This Object operation,
      @Advice.FieldValue("namespace") MongoNamespace namespace,
      @Advice.Origin("#t") String operationClassName,
      @Advice.Argument(value = 1, readOnly = false) SingleResultCallback<T> callback) {


    final String operationClass = operation instanceof HasOperationName ?
        ((HasOperationName) operation).name() :
        operationClassName;

    final Span clientSpan = MongoClientInstrumentation.clientSpanBuilder(namespace, operationClass).start();
    callback = spanCompletingCallback(callback, Kamon.currentContext().withEntry(Span.Key(), clientSpan));
  }

  public static <T> SingleResultCallback<T> spanCompletingCallback(SingleResultCallback<T> originalCallback, Context context) {
    return new SingleResultCallback<T>() {

      @Override
      public void onResult(T result, Throwable t) {
        try {

          final Span span = context.get(Span.Key());

          if(result != null) {
            if(result instanceof BulkWriteResult) {
              final BulkWriteResult bulkResult = (BulkWriteResult) result;

              span
                .tag("mongo.bulk.ack", bulkResult.wasAcknowledged())
                .tag("mongo.bulk.inserted", bulkResult.getInsertedCount())
                .tag("mongo.bulk.modified", bulkResult.getModifiedCount())
                .tag("mongo.bulk.matched", bulkResult.getMatchedCount())
                .tag("mongo.bulk.deleted", bulkResult.getDeletedCount());
            }

            if(result instanceof AsyncBatchCursor && result instanceof HasContext) {
              ((HasContext) result).setContext(Context.of(Span.Key(), span));
            }

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
