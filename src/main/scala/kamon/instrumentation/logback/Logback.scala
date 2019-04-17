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

package kamon.instrumentation.logback

import com.typesafe.config.Config
import kamon.Kamon
import kamon.context.Context
import kamon.context.Context.Key

import scala.collection.JavaConverters._


object Logback {

  private var _mdcContextPropagation: Boolean = true
  private var _mdcTraceKey: String = "kamonTraceID"
  private var _mdcSpanKey: String = "kamonSpanID"
  private var _mdcKeys: Set[Key[Option[String]]] = Set.empty[Key[Option[String]]]

  def mdcTraceKey: String = _mdcTraceKey
  def mdcSpanKey: String = _mdcSpanKey
  def mdcContextPropagation: Boolean = _mdcContextPropagation
  def mdcKeys: Set[Key[Option[String]]] = _mdcKeys

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
    _mdcKeys = logbackConfig.getStringList("mdc-traced-keys").asScala.toSet.map { key: String => Context.key[Option[String]](key, None) }
  }
}

/**
  * Mixin for ch.qos.logback.classic.spi.ILoggingEvent
  */
trait ContextAwareLoggingEvent {
  def getContext: Context
  def setContext(context:Context):Unit
}
