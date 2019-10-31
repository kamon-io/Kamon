package kamon.instrumentation.mongo;

import com.mongodb.MongoNamespace;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.operation.BatchCursor;
import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage;
import kamon.instrumentation.context.HasContext;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

public class ExecuteOperationAdvice {

  @Advice.OnMethodEnter()
  public static <T> Storage.Scope enter(
      @Advice.This Object operation,
      @Advice.FieldValue("namespace") MongoNamespace namespace,
      @Advice.Origin("#t") String operationClassName) {


    final String operationClass = operation instanceof MongoClientInstrumentation.HasOperationName ?
        ((MongoClientInstrumentation.HasOperationName) operation).name() :
        operationClassName;

    final Span clientSpan = MongoClientInstrumentation.clientSpanBuilder(namespace, operationClass).start();
    System.out.println("Created a Span for operation " + clientSpan.operationName());

    return Kamon.storeContext(Kamon.currentContext().withEntry(Span.Key(), clientSpan));
  }

  @Advice.OnMethodExit
  public static void exit(@Advice.Enter Storage.Scope scope, @Advice.Return Object result) {
    final Span span = scope.context().get(Span.Key());

    if(result instanceof BatchCursor && result instanceof HasContext) {
      ((HasContext) result).setContext(Context.of(Span.Key(), span));
    }

    if(result instanceof BulkWriteResult) {
      final BulkWriteResult bulkResult = (BulkWriteResult) result;

      span
        .tag("mongo.bulk.ack", bulkResult.wasAcknowledged())
        .tag("mongo.bulk.inserted", bulkResult.getInsertedCount())
        .tag("mongo.bulk.modified", bulkResult.getModifiedCount())
        .tag("mongo.bulk.matched", bulkResult.getMatchedCount())
        .tag("mongo.bulk.deleted", bulkResult.getDeletedCount());
    }

    span.finish();
    scope.close();
  }
}
