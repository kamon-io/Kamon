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

package kamon.logback.instrumentation


import com.typesafe.config.Config
import kamon.context.Context
import kamon.Kamon
import kamon.Configuration.OnReconfigureHook

object Logback {

  @volatile private var _mdcContextPropagation: Boolean = true
  @volatile private var _mdcTraceKey: String = "kamonTraceID"
  @volatile private var _mdcSpanKey: String = "kamonSpanID"

  def mdcTraceKey: String = _mdcTraceKey
  def mdcSpanKey: String = _mdcSpanKey
  def mdcContextPropagation: Boolean = _mdcContextPropagation

  loadConfiguration(Kamon.config())

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit =
      Logback.loadConfiguration(newConfig)
  })

  private def loadConfiguration(config: Config): Unit = synchronized {
    val logbackConfig = config.getConfig("kamon.logback")
    _mdcContextPropagation = logbackConfig.getBoolean("mdc-context-propagation")
    _mdcTraceKey = logbackConfig.getString("mdc-trace-id-key")
    _mdcSpanKey = logbackConfig.getString("mdc-span-id-key")
  }
}

/**
  * Mixin for ch.qos.logback.classic.spi.ILoggingEvent
  */
trait ContextAwareLoggingEvent {
  def getContext: Context
  def setContext(context:Context):Unit
}
