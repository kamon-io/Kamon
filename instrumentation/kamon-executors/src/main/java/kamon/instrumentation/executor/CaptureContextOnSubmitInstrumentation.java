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

import com.typesafe.config.Config;
import kamon.Kamon;
import kamon.instrumentation.executor.CaptureContextOnSubmitAdvices.CallableCollectionWrapperAdvisor;
import kamon.instrumentation.executor.CaptureContextOnSubmitAdvices.CallableWrapperAdvisor;
import kamon.instrumentation.executor.CaptureContextOnSubmitAdvices.RunnableWrapperAdvisor;
import kamon.instrumentation.executor.ContextAware.ContextAwareCallableProvider;
import kamon.instrumentation.executor.ContextAware.ContextAwareRunnableProvider;
import kamon.instrumentation.executor.ContextAware.DefaultContextAwareCallable;
import kamon.instrumentation.executor.ContextAware.DefaultContextAwareRunnable;
import kanela.agent.api.instrumentation.InstrumentationBuilder;
import kanela.agent.bootstrap.context.ContextHandler;
import kanela.agent.bootstrap.context.ContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static java.text.MessageFormat.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public final class CaptureContextOnSubmitInstrumentation extends InstrumentationBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(CaptureContextOnSubmitInstrumentation.class);

    private volatile static Settings settings = readSettings(Kamon.config());

    public CaptureContextOnSubmitInstrumentation() {

        /**
         * Set the ContextProvider
         */
        ContextHandler.setContextProvider(new KamonContextProvider());

        Kamon.onReconfigure(newConfig -> { settings = readSettings(newConfig); });

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

    private static final class Settings {
        public final List<ContextAwareRunnableProvider> runnableAwareProviders;
        public final List<ContextAwareCallableProvider> callableAwareProviders;

        private Settings(
                List<ContextAwareRunnableProvider> runnableAwareProviders,
                List<ContextAwareCallableProvider> callableAwareProviders
        ) {
            this.runnableAwareProviders = runnableAwareProviders;
            this.callableAwareProviders = callableAwareProviders;
        }
    }

    private static Settings readSettings(Config config) {
        Config executorCaptureConfig = config.getConfig("kanela.modules.executor-service-capture-on-submit");
        List<ContextAwareRunnableProvider> runnableAwareProviders ;
        if (executorCaptureConfig.hasPath("context-aware-runnable-providers")) {
            runnableAwareProviders = executorCaptureConfig.getStringList("context-aware-runnable-providers")
                    .stream()
                    .map(CaptureContextOnSubmitInstrumentation::loadRunnableProvider)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(toList());
        } else {
            runnableAwareProviders = emptyList();
        }

        List<ContextAwareCallableProvider> callableAwareProviders;
        if (executorCaptureConfig.hasPath("context-aware-callable-providers")) {
            callableAwareProviders = executorCaptureConfig.getStringList("context-aware-callable-providers")
                    .stream()
                    .map(CaptureContextOnSubmitInstrumentation::loadCallableProvider)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(toList());
        } else {
            callableAwareProviders = emptyList();
        }

        return new Settings(runnableAwareProviders, callableAwareProviders);
    }

    private static Optional<ContextAwareRunnableProvider> loadRunnableProvider(String providerClassName) {
        Optional<ContextAwareRunnableProvider> providerOpt;
        try {
            providerOpt = Optional.of(
                    (ContextAwareRunnableProvider) Class.forName(providerClassName).getConstructor().newInstance()
            );
        } catch (Exception e) {
            LOG.warn(format("Error trying to load ContextAwareRunnableProvider: {0}.", providerClassName), e);
            providerOpt = Optional.empty();
        }
        return providerOpt;
    }

    private static Optional<ContextAwareCallableProvider> loadCallableProvider(String providerClassName) {
        Optional<ContextAwareCallableProvider> providerOpt;
        try {
            providerOpt = Optional.of(
                    (ContextAwareCallableProvider) Class.forName(providerClassName).getConstructor().newInstance()
            );
        } catch (Exception e) {
            LOG.warn(format("Error trying to load ContextAwareCallableProvider: {0}.", providerClassName), e);
            providerOpt = Optional.empty();
        }
        return providerOpt;
    }

    /**
     * implementation of kanela.agent.bootstrap.context.ContextProvider
     */
    private static class KamonContextProvider implements ContextProvider {

        @Override
        public Runnable wrapInContextAware(Runnable r) {
            return settings.runnableAwareProviders
                    .stream()
                    .filter(p -> p.test(r))
                    .findFirst()
                    .map(it -> it.provide(r))
                    .orElse(new DefaultContextAwareRunnable(r));
        }

        @SuppressWarnings("rawtypes")
        @Override
        public <A> Callable wrapInContextAware(Callable<A> c) {
            return settings.callableAwareProviders
                    .stream()
                    .filter(p -> p.test(c))
                    .findFirst()
                    .map(it -> it.provide(c))
                    .orElse(new DefaultContextAwareCallable<>(c));
        }
    }
}
