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

          if (t == null) {
            if (result instanceof BulkWriteResult) {
              final BulkWriteResult bulkResult = (BulkWriteResult) result;

              span
                .tag("mongo.bulk.ack", bulkResult.wasAcknowledged())
                .tag("mongo.bulk.inserted", bulkResult.getInsertedCount())
                .tag("mongo.bulk.modified", bulkResult.getModifiedCount())
                .tag("mongo.bulk.matched", bulkResult.getMatchedCount())
                .tag("mongo.bulk.deleted", bulkResult.getDeletedCount());
            }

            if (result instanceof AsyncBatchCursor && result instanceof HasContext) {
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
