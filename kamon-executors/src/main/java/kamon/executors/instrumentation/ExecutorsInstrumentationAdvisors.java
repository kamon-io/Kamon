/* =========================================================================================
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

package kamon.executors.instrumentation;

import kanela.agent.bootstrap.context.ContextHandler;
import kanela.agent.libs.net.bytebuddy.asm.Advice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;

final class ExecutorsInstrumentationAdvisors {

    public static class RunnableWrapperAdvisor {
        /**
         * Wraps a {@link Runnable} so that it executes with the current context.
         */
        @Advice.OnMethodEnter
        public static void wrapParam(@Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
            runnable = ContextHandler.wrapInContextAware(runnable);
        }
    }

    public static class CallableWrapperAdvisor {
        /**
         * Wraps a {@link Callable} so that it executes with the current context.
         */
        @Advice.OnMethodEnter
        public static void wrapParam(@Advice.Argument(value = 0, readOnly = false) Callable<?> callable) {
            callable = ContextHandler.wrapInContextAware(callable);
        }
    }

    public static class CallableCollectionWrapperAdvisor {
        /**
         * Wraps all elements of a list of {@link Callable}'s so that it executes with the current context.
         */
        @Advice.OnMethodEnter
        public static void wrapParam(@Advice.Argument(value = 0, readOnly = false) Collection<? extends Callable<?>> tasks) {
            final Collection<Callable<?>> wrappedTasks = new ArrayList<>(tasks.size());
            for (Callable<?> task : tasks) {
                if(task != null) wrappedTasks.add(ContextHandler.wrapInContextAware(task));
            }
            tasks = wrappedTasks;
        }
    }
}