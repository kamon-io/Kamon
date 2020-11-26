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
import kamon.instrumentation.context.HasContext;
import kamon.trace.Span;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

import java.util.List;

public class AsyncBatchCursorGetMoreAdvice {

  @Advice.OnMethodEnter
  public static <T> void enter(
      @Advice.This Object batchCursor,
      @Advice.FieldValue("namespace") MongoNamespace namespace,
      @Advice.Argument(value = 2, readOnly = false) SingleResultCallback<T> callback) {

    final Span parentSpan = ((HasContext) batchCursor).context().get(Span.Key());
    final Span getMoreSpan = MongoClientInstrumentation.getMoreSpanBuilder(parentSpan, namespace).start();
    callback = spanCompletingCallback(callback, getMoreSpan);
  }

  public static <T> SingleResultCallback<T> spanCompletingCallback(SingleResultCallback<T> originalCallback, Span span) {
    return new SingleResultCallback<T>() {

      @Override
      public void onResult(T result, Throwable t) {
        try {
          if(t == null) {
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
