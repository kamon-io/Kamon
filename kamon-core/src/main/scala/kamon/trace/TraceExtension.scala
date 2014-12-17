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

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.actor
import akka.event.Logging
import kamon._
import kamon.metric.Metrics
import kamon.util.GlobPathFilter

class TraceExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val config = system.settings.config.getConfig("kamon.trace")
  val dispatcher = system.dispatchers.lookup(config.getString("dispatcher"))

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
      case "threshold" ⇒ new ThresholdSampler(config.getDuration("threshold-sampler.minimum-elapsed-time", TimeUnit.NANOSECONDS))
    }

  val log = Logging(system, "TraceExtension")
  val subscriptions = system.actorOf(Props[TraceSubscriptions], "trace-subscriptions")
  val incubator = system.actorOf(Incubator.props(subscriptions))
  val metricsExtension = Kamon(Metrics)(system)

  def newTraceContext(traceName: String, token: String, origin: TraceContextOrigin, system: ActorSystem): TraceContext =
    newTraceContext(traceName, token, true, origin, RelativeNanoTimestamp.now, system)

  def newTraceContext(traceName: String, token: String, isOpen: Boolean, origin: TraceContextOrigin,
    startTimestamp: RelativeNanoTimestamp, system: ActorSystem): TraceContext = {
    def newMetricsOnlyContext = new MetricsOnlyContext(traceName, token, isOpen, detailLevel, origin, startTimestamp, log, metricsExtension, system)

    if (detailLevel == LevelOfDetail.MetricsOnly || origin == TraceContextOrigin.Remote)
      newMetricsOnlyContext
    else {
      if (!sampler.shouldTrace)
        newMetricsOnlyContext
      else
        new TracingContext(traceName, token, true, detailLevel, origin, startTimestamp, log, metricsExtension, this, system)
    }
  }

  def subscribe(subscriber: ActorRef): Unit = subscriptions ! TraceSubscriptions.Subscribe(subscriber)
  def unsubscribe(subscriber: ActorRef): Unit = subscriptions ! TraceSubscriptions.Unsubscribe(subscriber)

  private[kamon] def dispatchTracingContext(trace: TracingContext): Unit =
    if (sampler.shouldReport(trace.elapsedTime))
      if (trace.shouldIncubate)
        incubator ! trace
      else
        subscriptions ! trace.generateTraceInfo

}

object Trace extends ExtensionId[TraceExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = Trace
  def createExtension(system: ExtendedActorSystem): TraceExtension = new TraceExtension(system)

  case class MetricGroupFilter(includes: List[GlobPathFilter], excludes: List[GlobPathFilter]) {
    def accept(name: String): Boolean = includes.exists(_.accept(name)) && !excludes.exists(_.accept(name))
  }
}

case class TraceInfo(name: String, token: String, timestamp: NanoTimestamp, elapsedTime: NanoInterval, metadata: Map[String, String], segments: List[SegmentInfo])
case class SegmentInfo(name: String, category: String, library: String, timestamp: NanoTimestamp, elapsedTime: NanoInterval, metadata: Map[String, String])