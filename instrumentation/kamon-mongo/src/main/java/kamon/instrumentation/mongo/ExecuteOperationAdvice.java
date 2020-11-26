/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
