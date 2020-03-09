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

import kamon.annotation.instrumentation.cache.AnnotationCache;
import kamon.annotation.util.Hooks;
import kamon.metric.Timer;
import kamon.util.CallingThreadExecutionContext$;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import kanela.agent.libs.net.bytebuddy.implementation.bytecode.assign.Assigner;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

public final class TimeAnnotationAdvisor {

  public final static ExecutionContext CallingThreadEC = CallingThreadExecutionContext$.MODULE$;

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void start(
      @Advice.This(optional = true) Object obj,
      @Advice.Origin Class<?> clazz,
      @Advice.Origin Method method,
      @Advice.Origin("#t") String className,
      @Advice.Origin("#m") String methodName,
      @Advice.Local("startedTimer") Timer.Started startedTimer) {

    final Timer timer = AnnotationCache.getTimer(method, obj, clazz, className, methodName);
    startedTimer = timer.start();
  }

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void end(
      @Advice.Local("startedTimer") Timer.Started startedTimer,
      @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object result) {

    if (result instanceof Future) {
      Hooks.stopTimerOnComplete((Future<?>) result, startedTimer);

    } else if (result instanceof CompletionStage) {
      ((CompletionStage<?>) result).handle(new CompletionStageCompleteFunction(startedTimer));

    } else {
      startedTimer.stop();
    }
  }

  public static class CompletionStageCompleteFunction<T> implements BiFunction<T, Throwable, Object> {
    private final Timer.Started timer;

    public CompletionStageCompleteFunction(Timer.Started timer) {
      this.timer = timer;
    }

    @Override
    public Object apply(T t, Throwable throwable) {
      timer.stop();
      return null;
    }
  }
}