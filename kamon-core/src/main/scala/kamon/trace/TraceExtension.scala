/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.trace

import akka.actor._
import akka.actor
import akka.event.Logging
import kamon.metric.Metrics
import kamon.util.GlobPathFilter
import kamon.Kamon

class TraceExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val config = system.settings.config.getConfig("kamon.trace")
  val enableAskPatternTracing = config.getBoolean("ask-pattern-tracing")

  val detailLevel: LevelOfDetail = config.getString("level") match {
    case "metrics-only" ⇒ LevelOfDetail.MetricsOnly
    case "simple-trace" ⇒ LevelOfDetail.SimpleTrace
    case other          ⇒ sys.error(s"Unknown tracing level $other present in the configuration file.")
  }

  val sampler: Sampler =
    if (detailLevel == LevelOfDetail.MetricsOnly) NoSampling
    else config.getString("sampling") match {
      case "all"       ⇒ SampleAll
      case "random"    ⇒ new RandomSampler(config.getInt("random-sampler.chance"))
      case "ordered"   ⇒ new OrderedSampler(config.getInt("ordered-sampler.interval"))
      case "threshold" ⇒ new RandomSampler(config.getInt("threshold-sampler.threshold"))
    }

  val log = Logging(system, "TraceExtension")
  val subscriptions = system.actorOf(Props[TraceSubscriptions], "trace-subscriptions")
  val incubator = system.actorOf(Incubator.props(subscriptions, 10000000000L))
  val metricsExtension = Kamon(Metrics)(system)

  def newTraceContext(traceName: String, token: String, isOpen: Boolean, origin: TraceContextOrigin, nanoTimestamp: Long, system: ActorSystem): TraceContext = {
    def newMetricsOnlyContext = new MetricsOnlyContext(traceName, token, true, detailLevel, origin, nanoTimestamp, log, metricsExtension, system)

    if (detailLevel == LevelOfDetail.MetricsOnly || origin == TraceContextOrigin.Remote)
      newMetricsOnlyContext
    else {
      if (!sampler.shouldTrace)
        newMetricsOnlyContext
      else
        new TracingContext(traceName, token, true, detailLevel, origin, nanoTimestamp, log, this, metricsExtension, system)
    }
  }

  def report(trace: TracingContext): Unit = if (sampler.shouldReport(trace.elapsedNanoTime)) {
    if (trace.shouldIncubate)
      incubator ! trace
    else
      trace.generateTraceInfo.map(subscriptions ! _)
  }

  def subscribe(subscriber: ActorRef): Unit = subscriptions ! TraceSubscriptions.Subscribe(subscriber)
  def unsubscribe(subscriber: ActorRef): Unit = subscriptions ! TraceSubscriptions.Unsubscribe(subscriber)

}

object Trace extends ExtensionId[TraceExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = Trace
  def createExtension(system: ExtendedActorSystem): TraceExtension = new TraceExtension(system)

  case class MetricGroupFilter(includes: List[GlobPathFilter], excludes: List[GlobPathFilter]) {
    def accept(name: String): Boolean = includes.exists(_.accept(name)) && !excludes.exists(_.accept(name))
  }
}

case class TraceInfo(name: String, token: String, startMilliTime: Long, startNanoTime: Long, elapsedNanoTime: Long, metadata: Map[String, String], segments: List[SegmentInfo])
case class SegmentInfo(name: String, category: String, library: String, startNanoTime: Long, elapsedNanoTime: Long, metadata: Map[String, String])