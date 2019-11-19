/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.annotation.instrumentation.advisor;

import kamon.Kamon;
import kamon.annotation.instrumentation.cache.AnnotationCache;
import kamon.context.Storage;
import kamon.trace.Span;
import kamon.trace.SpanBuilder;
import kamon.util.CallingThreadExecutionContext$;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import kanela.agent.libs.net.bytebuddy.implementation.bytecode.assign.Assigner;
import scala.Function1;
import scala.Unit;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.util.Try;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class TraceAnnotationAdvisor {

  public final static ExecutionContext CallingThreadEC = CallingThreadExecutionContext$.MODULE$;

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void startSpan(
      @Advice.This(optional = true) Object obj,
      @Advice.Origin Class<?> clazz,
      @Advice.Origin Method method,
      @Advice.Origin("#t") String className,
      @Advice.Origin("#m") String methodName,
      @Advice.Local("span") Span span,
      @Advice.Local("scope") Storage.Scope scope) {

    final SpanBuilder builder = AnnotationCache.getSpanBuilder(method, obj, clazz, className, methodName);
    span = builder.start();
    scope = Kamon.storeContext(Kamon.currentContext().withEntry(span.Key(), span));
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Local("span") Span span,
      @Advice.Local("scope") Storage.Scope scope,
      @Advice.Thrown Throwable throwable,
      @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object result) {

    scope.close();

    if (result instanceof Future) {
      ((Future<?>) result).onComplete(new FutureCompleteFunction(span), CallingThreadEC);

    } else if (result instanceof CompletionStage) {
      ((CompletionStage<?>) result).handle(new CompletionStageCompleteFunction(span));

    } else {
      if (throwable != null)
        span.fail(throwable.getMessage(), throwable);

      span.finish();
    }
  }

  public static class FutureCompleteFunction<T> implements Function1<Try<T>, Object> {
    private final Span span;

    public FutureCompleteFunction(Span span) {
      this.span = span;
    }

    @Override
    public Object apply(Try<T> result) {
      if(result.isSuccess()) {
        span.finish();
      } else {
        span.fail(result.failed().get()).finish();
      }

      return null;
    }
  }

  public static class CompletionStageCompleteFunction<T> implements BiFunction<T, Throwable, Object> {
    private final Span span;

    public CompletionStageCompleteFunction(Span span) {
      this.span = span;
    }

    @Override
    public Object apply(T t, Throwable throwable) {
      if(throwable == null) {
        span.finish();
      } else {
        span.fail(throwable).finish();
      }

      return null;
    }
  }
}