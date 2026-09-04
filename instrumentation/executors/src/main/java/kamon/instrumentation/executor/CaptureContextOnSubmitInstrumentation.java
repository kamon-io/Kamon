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

import java.util.Collection;
import java.util.concurrent.Callable;
import kamon.instrumentation.executor.CaptureContextOnSubmitAdvices.CallableCollectionWrapperAdvisor;
import kamon.instrumentation.executor.CaptureContextOnSubmitAdvices.CallableWrapperAdvisor;
import kamon.instrumentation.executor.CaptureContextOnSubmitAdvices.RunnableWrapperAdvisor;
import kamon.instrumentation.executor.ContextAware.DefaultContextAwareCallable;
import kamon.instrumentation.executor.ContextAware.DefaultContextAwareRunnable;
import kanela.agent.api.instrumentation.InstrumentationBuilder;
import kanela.agent.bootstrap.ContextApi;
import kanela.agent.bootstrap.ContextApiImplementation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CaptureContextOnSubmitInstrumentation extends InstrumentationBuilder {

  private static final Logger LOG =
      LoggerFactory.getLogger(CaptureContextOnSubmitInstrumentation.class);

  public CaptureContextOnSubmitInstrumentation() {

    /** Set the ContextProvider */
    ContextApi.setContextApiImplementation(new KamonContextApiImplementation());

    /**
     * Instrument all implementations of:
     *
     * <p>java.util.concurrent.Executor::execute
     */
    onSubTypesOf("java.util.concurrent.Executor")
        .advise(method("execute").and(withArgument(Runnable.class)), RunnableWrapperAdvisor.class);

    /**
     * Instrument all implementations of:
     *
     * <p>java.util.concurrent.ExecutorService::submit(Runnable)
     * java.util.concurrent.ExecutorService::submit(Callable)
     * java.util.concurrent.ExecutorService::[invokeAny|invokeAll](Collection[Callable])
     */
    onSubTypesOf("java.util.concurrent.ExecutorService")
        .advise(method("submit").and(withArgument(Runnable.class)), RunnableWrapperAdvisor.class)
        .advise(method("submit").and(withArgument(Callable.class)), CallableWrapperAdvisor.class)
        .advise(
            anyMethods("invokeAny", "invokeAll").and(withArgument(Collection.class)),
            CallableCollectionWrapperAdvisor.class);

    /**
     * Instrument all implementations of:
     *
     * <p>java.util.concurrent.ScheduledExecutorService::schedule(Runnable, long, TimeUnit)
     * java.util.concurrent.ScheduledExecutorService::schedule(Callable, long, TimeUnit)
     */
    onSubTypesOf("java.util.concurrent.ScheduledExecutorService")
        .advise(
            method("schedule").and(withArgument(0, Runnable.class)), RunnableWrapperAdvisor.class)
        .advise(
            method("schedule").and(withArgument(0, Callable.class)), CallableWrapperAdvisor.class);
  }

  private static class KamonContextApiImplementation implements ContextApiImplementation {

    @Override
    public Runnable wrapRunnable(Runnable r) {
      return new DefaultContextAwareRunnable(r);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <A> Callable wrapCallable(Callable<A> c) {
      return new DefaultContextAwareCallable<>(c);
    }
  }
}
