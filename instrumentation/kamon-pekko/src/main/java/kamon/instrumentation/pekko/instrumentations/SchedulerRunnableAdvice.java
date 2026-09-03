/* =========================================================================================
 * Copyright © 2013-2022 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.instrumentation.pekko.instrumentations;

import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage;
import kanela.agent.libs.net.bytebuddy.asm.Advice;
import org.apache.pekko.actor.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchedulerRunnableAdvice {
  private static final Logger log = LoggerFactory.getLogger(SchedulerRunnableAdvice.class);

  private static final Class<?> runOnClose() {
    try {
      return Class.forName("org.apache.pekko.actor.TaskRunOnClose");
    } catch (ClassNotFoundException e) {
      return null;
    } catch (Throwable e) {
      log.error("Unable to retain TaskRunOnClose marker trait on akka runnables", e);
      return null;
    }
  }

  public static final Class<?> taskRunOnClose = runOnClose();

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void enter(@Advice.Argument(value = 1, readOnly = false) Runnable runnable) {
    if (taskRunOnClose != null && taskRunOnClose.isAssignableFrom(runnable.getClass())) {
      runnable = new TaskRunOnCloseContextAwareRunnable(Kamon.currentContext(), runnable);
    } else {
      runnable = new ContextAwareRunnable(Kamon.currentContext(), runnable);
    }
  }

  public static class ContextAwareRunnable implements Runnable {
    private final Context context;
    private final Runnable underlyingRunnable;

    public ContextAwareRunnable(Context context, Runnable underlyingRunnable) {
      this.context = context;
      this.underlyingRunnable = underlyingRunnable;
    }

    @Override
    public void run() {
      final Storage.Scope scope = Kamon.storeContext(context);

      try {
        underlyingRunnable.run();
      } finally {
        scope.close();
      }
    }
  }

  public static class TaskRunOnCloseContextAwareRunnable implements Scheduler.TaskRunOnClose {
    private final Context context;
    private final Runnable underlyingRunnable;

    public TaskRunOnCloseContextAwareRunnable(Context context, Runnable underlyingRunnable) {
      this.context = context;
      this.underlyingRunnable = underlyingRunnable;
    }

    @Override
    public void run() {
      final Storage.Scope scope = Kamon.storeContext(context);

      try {
        underlyingRunnable.run();
      } finally {
        scope.close();
      }
    }
  }
}
