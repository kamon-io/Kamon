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

import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage.Scope;
import kamon.executors.instrumentation.ExecutorsInstrumentationAdvisors.CallableCollectionWrapperAdvisor;
import kamon.executors.instrumentation.ExecutorsInstrumentationAdvisors.CallableWrapperAdvisor;
import kamon.executors.instrumentation.ExecutorsInstrumentationAdvisors.RunnableWrapperAdvisor;
import kanela.agent.api.instrumentation.KanelaInstrumentation;
import kanela.agent.bootstrap.context.ContextHandler;
import kanela.agent.bootstrap.context.ContextProvider;

import java.util.Collection;
import java.util.concurrent.Callable;

public final class ExecutorInstrumentation extends KanelaInstrumentation {

    public ExecutorInstrumentation() {
        /**
         * Set the ContextProvider
         */
        ContextHandler.setContexProvider(new KamonContextProvider());

        /**
         * Instrument all implementations of:
         *
         * java.util.concurrent.Executor::execute
         *
         */
        forSubtypeOf(() -> "java.util.concurrent.Executor", builder ->
                builder
                    .withAdvisorFor(method("execute").and(withArgument(Runnable.class)), () -> RunnableWrapperAdvisor.class)
                    .build());


        /**
         * Instrument all implementations of:
         *
         * java.util.concurrent.ExecutorService::submit(Runnable)
         * java.util.concurrent.ExecutorService::submit(Callable)
         * java.util.concurrent.ExecutorService::[invokeAny|invokeAll](Collection[Callable])
         *
         */
        forSubtypeOf(() -> "java.util.concurrent.ExecutorService", builder ->
                builder
                    .withAdvisorFor(method("submit").and(withArgument(Runnable.class)), () -> RunnableWrapperAdvisor.class)
                    .withAdvisorFor(method("submit").and(withArgument(Callable.class)), () -> CallableWrapperAdvisor.class)
                    .withAdvisorFor(anyMethods("invokeAny", "invokeAll").and(withArgument(Collection.class)), () -> CallableCollectionWrapperAdvisor.class)
                    .build());
    }

    /**
     * Runs a Runnable within Kamon Context
     */
    private static class ContextAwareRunnable implements Runnable {

        private final Runnable underlying;
        private final Context context;

        ContextAwareRunnable(Runnable r) {
            this.context = Kamon.currentContext();
            this.underlying = r;
        }

        @Override
        public void run() {
            final Scope scope = Kamon.storeContext(context);
            try {
                underlying.run();
            } finally {
                scope.close();
            }
        }
    }

    /**
     * Runs a Callable within Kamon Context
     */
    private static class ContextAwareCallable<A> implements Callable<A> {

        private final Callable<A> underlying;
        private final Context context;

        ContextAwareCallable(Callable<A> c) {
            this.context = Kamon.currentContext();
            this.underlying = c;
        }

        public A call() throws Exception {
            final Scope scope = Kamon.storeContext(context);
            try {
                return underlying.call();
            } finally {
                scope.close();
            }
        }
    }

    /**
     * implementation of kanela.agent.bootstrap.context.ContextProvider
     */
    private static class KamonContextProvider implements ContextProvider {
        @Override
        public Runnable wrapInContextAware(Runnable runnable) {
            return new ContextAwareRunnable(runnable);
        }

        @Override
        public <A> Callable wrapInContextAware(Callable<A> callable) {
            return new ContextAwareCallable<>(callable);
        }
    }
}