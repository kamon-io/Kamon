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

package kamon.instrumentation.executor;

import kamon.Kamon;
import kamon.context.Context;
import kamon.context.Storage.Scope;
import kamon.instrumentation.executor.CaptureContextOnSubmitAdvices.CallableCollectionWrapperAdvisor;
import kamon.instrumentation.executor.CaptureContextOnSubmitAdvices.CallableWrapperAdvisor;
import kamon.instrumentation.executor.CaptureContextOnSubmitAdvices.RunnableWrapperAdvisor;
import kanela.agent.api.instrumentation.InstrumentationBuilder;
import kanela.agent.bootstrap.context.ContextHandler;
import kanela.agent.bootstrap.context.ContextProvider;

import java.util.Collection;
import java.util.concurrent.Callable;

public final class CaptureContextOnSubmitInstrumentation extends InstrumentationBuilder {

    public CaptureContextOnSubmitInstrumentation() {

        /**
         * Set the ContextProvider
         */
        ContextHandler.setContextProvider(new KamonContextProvider());

        /**
         * Instrument all implementations of:
         *
         * java.util.concurrent.Executor::execute
         *
         */
        onSubTypesOf("java.util.concurrent.Executor")
                .advise(method("execute").and(withArgument(Runnable.class)), RunnableWrapperAdvisor.class);


        /**
         * Instrument all implementations of:
         *
         * java.util.concurrent.ExecutorService::submit(Runnable)
         * java.util.concurrent.ExecutorService::submit(Callable)
         * java.util.concurrent.ExecutorService::[invokeAny|invokeAll](Collection[Callable])
         *
         */
        onSubTypesOf( "java.util.concurrent.ExecutorService")
                .advise(method("submit").and(withArgument(Runnable.class)), RunnableWrapperAdvisor.class)
                .advise(method("submit").and(withArgument(Callable.class)), CallableWrapperAdvisor.class)
                .advise(anyMethods("invokeAny", "invokeAll").and(withArgument(Collection.class)), CallableCollectionWrapperAdvisor.class);

        /**
         * Instrument all implementations of:
         *
         * java.util.concurrent.ScheduledExecutorService::schedule(Runnable, long, TimeUnit)
         * java.util.concurrent.ScheduledExecutorService::schedule(Callable, long, TimeUnit)
         *
         */
        onSubTypesOf("java.util.concurrent.ScheduledExecutorService")
                .advise(method("schedule").and(withArgument(0, Runnable.class)), RunnableWrapperAdvisor.class)
                .advise(method("schedule").and(withArgument(0, Callable.class)), CallableWrapperAdvisor.class);

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