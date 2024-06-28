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

package kamon.testkit

import com.typesafe.config.ConfigFactory
import kamon.Kamon

trait Reconfigure {

  /**
    * Makes Kamon flush metric snapshots to reporters every millisecond
    */
  def enableFastMetricFlushing(): Unit =
    applyConfig("kamon.metric.tick-interval = 1 millisecond")

  /**
    * Makes Kamon flush spans to reporters every millisecond
    */
  def enableFastSpanFlushing(): Unit =
    applyConfig("kamon.trace.tick-interval = 1 millisecond")

  /**
    * Makes Kamon sample all new traces
    */
  def sampleAlways(): Unit =
    applyConfig("kamon.trace.sampler = always")

  /**
    * Makes Kamon never sample a new trace
    */
  def sampleNever(): Unit =
    applyConfig("kamon.trace.sampler = never")

  /**
    * Enables scoping of Span metrics to their parent operation
    */
  def enableSpanMetricScoping(): Unit =
    applyConfig("kamon.trace.span-metric-tags.parent-operation = yes")

  /**
    * Disables scoping of Span metrics to their parent operation
    */
  def disableSpanMetricScoping(): Unit =
    applyConfig("kamon.trace.span-metric-tags.parent-operation = no")

  /**
    * Enables using the same Span identifier as their remote parent on server operations.
    */
  def enableJoiningRemoteParentWithSameId(): Unit =
    applyConfig("kamon.trace.join-remote-parents-with-same-span-id = yes")

  /**
    * Disables using the same Span identifier as their remote parent on server operations.
    */
  def disableJoiningRemoteParentWithSameId(): Unit =
    applyConfig("kamon.trace.join-remote-parents-with-same-span-id = no")

  /**
    * Parses the provided configuration and reconfigures Kamon with it
    */
  def applyConfig(configString: String): Unit =
    Kamon.reconfigure(ConfigFactory.parseString(configString).withFallback(Kamon.config()))

  /**
    * Resets Kamon's configuration what would be loaded by default.
    */
  def reset(): Unit =
    Kamon.reconfigure(ConfigFactory.load())

}

object Reconfigure extends Reconfigure
