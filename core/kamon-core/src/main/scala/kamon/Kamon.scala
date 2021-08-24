/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

package kamon

import kamon.metric.MetricRegistry
import kamon.trace.Tracer
import kamon.util.Clock

import java.util.concurrent.ScheduledExecutorService

//class Kamon {
//
//
//  def tracer: Tracer = ???
//  def clock: Clock = ???
//  def scheduler: ScheduledExecutorService = ???
//  def metricRegistry: MetricRegistry = ???
//
//}

object Kamon extends Configuration
  with Utilities
  with Metrics
  with Tracing
  with Modules
  with ContextPropagation
  with ContextStorage
  with CurrentStatus
  with Init {

//  private val _global = new Kamon()
}