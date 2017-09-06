/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.system

import java.time.Duration

import com.typesafe.config.Config
import kamon.{Kamon, OnReconfigureHook}
import org.slf4j.{Logger, LoggerFactory}

object SystemMetrics {

  val logger: Logger = LoggerFactory.getLogger("kamon.metrics.SystemMetrics")

  val FilterName = "system-metric"

  @volatile var sigarFolder:String = _
  @volatile var sigarRefreshInterval:Duration = _
  @volatile var sigarEnabled:Boolean = _
  @volatile var jmxEnabled: Boolean =_
  @volatile var contextSwitchesRefreshInterval:Duration = _

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit =
      SystemMetrics.loadConfiguration(newConfig)
  })

  private def loadConfiguration(config: Config): Unit = synchronized {
    val systemMetricsConfig = config.getConfig("kamon.system-metrics")
    
    sigarFolder = systemMetricsConfig.getString("sigar-native-folder")
    sigarRefreshInterval = systemMetricsConfig.getDuration("sigar-metrics-refresh-interval")
    sigarEnabled = systemMetricsConfig.getBoolean("sigar-enabled")
    jmxEnabled = systemMetricsConfig.getBoolean("jmx-enabled")
    contextSwitchesRefreshInterval = systemMetricsConfig.getDuration("context-switches-refresh-interval")
  }
}
