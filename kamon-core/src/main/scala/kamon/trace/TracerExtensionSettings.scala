/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.trace

import java.util.concurrent.TimeUnit
import kamon.util.ConfigTools.Syntax
import com.typesafe.config.Config

case class TraceSettings(levelOfDetail: LevelOfDetail, sampler: Sampler)

object TraceSettings {
  def apply(config: Config): TraceSettings = {
    val tracerConfig = config.getConfig("kamon.trace")

    val detailLevel: LevelOfDetail = tracerConfig.getString("level-of-detail") match {
      case "metrics-only" ⇒ LevelOfDetail.MetricsOnly
      case "simple-trace" ⇒ LevelOfDetail.SimpleTrace
      case other          ⇒ sys.error(s"Unknown tracer level of detail [$other] present in the configuration file.")
    }

    val sampler: Sampler =
      if (detailLevel == LevelOfDetail.MetricsOnly) NoSampling
      else tracerConfig.getString("sampling") match {
        case "all"       ⇒ SampleAll
        case "random"    ⇒ new RandomSampler(tracerConfig.getInt("random-sampler.chance"))
        case "ordered"   ⇒ new OrderedSampler(tracerConfig.getInt("ordered-sampler.interval"))
        case "threshold" ⇒ new ThresholdSampler(tracerConfig.getFiniteDuration("threshold-sampler.minimum-elapsed-time").toNanos)
      }

    TraceSettings(detailLevel, sampler)
  }
}