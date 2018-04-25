/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

package kamon.logback.instrumentation.kanela

import java.util.concurrent.Callable

import kamon.Kamon
import kamon.context.Context
import kamon.logback.instrumentation.{ContextAwareLoggingEvent, Logback}
import kamon.trace.{IdentityProvider, Span}
import kanela.agent.api.instrumentation.mixin.Initializer
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice.Argument
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{RuntimeType, SuperCall}
import kanela.agent.scala.KanelaInstrumentation
import org.slf4j.MDC

import scala.beans.BeanProperty

class AsyncAppenderInstrumentation extends KanelaInstrumentation {

  /**
    * Mix:
    *
    * ch.qos.logback.core.spi.DeferredProcessingAware with kamon.logback.mixin.ContextAwareLoggingEvent
    */
  forSubtypeOf("ch.qos.logback.core.spi.DeferredProcessingAware") { builder =>
    builder
      .withMixin(classOf[ContextAwareLoggingEventMixin])
      .build()
  }


  /**
    * Instrument:
    *
    * ch.qos.logback.core.AsyncAppenderBase::append
    */
  forTargetType("ch.qos.logback.core.AsyncAppenderBase") { builder =>
    builder
      .withAdvisorFor(method("append"), classOf[AppendMethodAdvisor])
      .build()
  }

  /**
    * Instrument:
    *
    * ch.qos.logback.core.spi.AppenderAttachableImpl::appendLoopOnAppenders
    */
  forTargetType("ch.qos.logback.core.spi.AppenderAttachableImpl") { builder =>
    builder
      .withInterceptorFor(method("appendLoopOnAppenders"), AppendLoopMethodInterceptor)
      .build()
  }

  /**
    * Instrument:
    *
    * ch.qos.logback.classic.util.LogbackMDCAdapter::getPropertyMap
    */
  forTargetType("ch.qos.logback.classic.util.LogbackMDCAdapter") { builder =>
    builder
      .withInterceptorFor(method("getPropertyMap"), GetPropertyMapMethodInterceptor)
      .build()
  }
}

/**
  * Advisor for ch.qos.logback.core.AsyncAppenderBase::append
  */
class AppendMethodAdvisor
object AppendMethodAdvisor {

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def onExit(@Argument(0) event:AnyRef): Unit =
    event.asInstanceOf[ContextAwareLoggingEvent].setContext(Kamon.currentContext())
}

/**
  * Interceptor for ch.qos.logback.core.spi.AppenderAttachableImpl::appendLoopOnAppenders
  */
object AppendLoopMethodInterceptor {

  @RuntimeType
  def aroundAppendLoop(@SuperCall callable: Callable[Int], @annotation.Argument(0) event:AnyRef): Int =
    Kamon.withContext(event.asInstanceOf[ContextAwareLoggingEvent].getContext)(callable.call())
}

/**
  * Interceptor for ch.qos.logback.classic.util.LogbackMDCAdapter::getPropertyMap
  */
object GetPropertyMapMethodInterceptor {

  @RuntimeType
  def aroundGetMDCPropertyMap(@SuperCall callable: Callable[_]): Any = {
    val context = Kamon.currentContext().get(Span.ContextKey)

    if (context.context().traceID != IdentityProvider.NoIdentifier && Logback.mdcContextPropagation){
      MDC.put(Logback.mdcTraceKey, context.context().traceID.string)
      MDC.put(Logback.mdcSpanKey, context.context().spanID.string)
      try {
        callable.call()
      } finally {
        MDC.remove(Logback.mdcTraceKey)
        MDC.remove(Logback.mdcSpanKey)
      }
    } else {
      callable.call()
    }
  }
}

/**
  * Mixin for ch.qos.logback.classic.spi.ILoggingEvent
  */
class ContextAwareLoggingEventMixin extends ContextAwareLoggingEvent {
  @volatile @BeanProperty var context:Context = _

  @Initializer
  def initialize():Unit =
    context = Kamon.currentContext()
}