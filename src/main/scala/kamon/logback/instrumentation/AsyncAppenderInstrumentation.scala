/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.logback.instrumentation


import com.typesafe.config.Config
import kamon.{Kamon, OnReconfigureHook}
import kamon.context.{Context, Key}
import kamon.trace.{IdentityProvider, Span}
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._
import org.slf4j.MDC
import scala.collection.JavaConverters._

import scala.beans.BeanProperty

object AsyncAppenderInstrumentation {

  @volatile private var _mdcContextPropagation: Boolean = true
  @volatile private var _mdcTraceKey: String = "kamonTraceID"
  @volatile private var _mdcSpanKey: String = "kamonSpanID"
  @volatile private var _mdcLocalKeys: Set[Key[Option[String]]] = Set.empty[Key[Option[String]]]
  @volatile private var _mdcBroadcastKeys: Set[Key[Option[String]]] = Set.empty[Key[Option[String]]]

  def mdcTraceKey: String = _mdcTraceKey
  def mdcSpanKey: String = _mdcSpanKey
  def mdcContextPropagation: Boolean = _mdcContextPropagation
  def mdcLocalKeys: Set[Key[Option[String]]] = _mdcLocalKeys
  def mdcBroadcastKeys: Set[Key[Option[String]]] = _mdcBroadcastKeys

  loadConfiguration(Kamon.config())

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit =
      AsyncAppenderInstrumentation.loadConfiguration(newConfig)
  })


  private def loadConfiguration(config: Config): Unit = synchronized {
    val logbackConfig = config.getConfig("kamon.logback")
    _mdcContextPropagation = logbackConfig.getBoolean("mdc-context-propagation")
    _mdcTraceKey = logbackConfig.getString("mdc-trace-id-key")
    _mdcSpanKey = logbackConfig.getString("mdc-span-id-key")
    _mdcLocalKeys = logbackConfig.getStringList("mdc-traced-local-keys").asScala.toSet.map { key: String => Key.local[Option[String]](key, None) }
    _mdcBroadcastKeys = logbackConfig.getStringList("mdc-traced-broadcast-keys").asScala.toSet.map { key: String => Key.broadcastString(key) }
  }
}

@Aspect
class AsyncAppenderInstrumentation {

  @DeclareMixin("ch.qos.logback.classic.spi.ILoggingEvent+")
  def mixinContextAwareLoggingToLoggingEvent: ContextAwareLoggingEvent =
    ContextAwareLoggingEvent()

  @Before("execution(* ch.qos.logback.core.AsyncAppenderBase.append(..)) && args(event)")
  def onAppend(event:ContextAwareLoggingEvent): Unit =
    event.setContext(Kamon.currentContext())

  @Around("execution(* ch.qos.logback.core.spi.AppenderAttachableImpl.appendLoopOnAppenders(..)) && args(event)")
  def onAppendLoopOnAppenders(pjp: ProceedingJoinPoint, event:ContextAwareLoggingEvent): Any =
    Kamon.withContext(event.getContext)(pjp.proceed())

  @Around("execution(* ch.qos.logback.classic.util.LogbackMDCAdapter.getPropertyMap())")
  def aroundGetMDCPropertyMap(pjp: ProceedingJoinPoint): Any = {

    val currentContext = Kamon.currentContext()
    val span = currentContext.get(Span.ContextKey)

    if (span.context().traceID != IdentityProvider.NoIdentifier && AsyncAppenderInstrumentation.mdcContextPropagation){
      MDC.put(AsyncAppenderInstrumentation.mdcTraceKey, span.context().traceID.string)
      MDC.put(AsyncAppenderInstrumentation.mdcSpanKey, span.context().spanID.string)

      AsyncAppenderInstrumentation.mdcLocalKeys.foreach { localKey =>
        currentContext.get(localKey).foreach { localKeyValue =>
          MDC.put(localKey.name, localKeyValue)
        }
      }

      AsyncAppenderInstrumentation.mdcBroadcastKeys.foreach { broadcast =>
        currentContext.get(broadcast).foreach { broadcastKeyValue =>
          MDC.put(broadcast.name, broadcastKeyValue)
        }
      }
      try {
        pjp.proceed()
      } finally {
        MDC.remove(AsyncAppenderInstrumentation.mdcTraceKey)
        MDC.remove(AsyncAppenderInstrumentation.mdcSpanKey)
        AsyncAppenderInstrumentation.mdcLocalKeys.foreach(key => MDC.remove(key.name))
        AsyncAppenderInstrumentation.mdcBroadcastKeys.foreach(key => MDC.remove(key.name))
      }
    } else {
      pjp.proceed()
    }
  }
}


trait ContextAwareLoggingEvent {
  def getContext: Context
  def setContext(context:Context):Unit
}

object ContextAwareLoggingEvent  {
  def apply() = new ContextAwareLoggingEvent {
    @volatile @BeanProperty var context: Context = Kamon.currentContext()
  }
}
