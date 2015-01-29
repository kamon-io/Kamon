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

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong

import akka.actor._
import akka.actor
import kamon.Kamon
import kamon.metric.{ Metrics, MetricsExtension }
import kamon.util.{ NanoInterval, RelativeNanoTimestamp, NanoTimestamp, GlobPathFilter }

import scala.util.Try

object Tracer extends ExtensionId[TracerExtension] with ExtensionIdProvider {
  override def get(system: ActorSystem): TracerExtension = super.get(system)
  def lookup(): ExtensionId[_ <: actor.Extension] = Tracer
  def createExtension(system: ExtendedActorSystem): TracerExtension = new TracerExtensionImpl(system)
}

trait TracerExtension extends Kamon.Extension {
  def newContext(name: String): TraceContext
  def newContext(name: String, token: String): TraceContext
  def newContext(name: String, token: String, timestamp: RelativeNanoTimestamp, isOpen: Boolean, isLocal: Boolean): TraceContext

  def subscribe(subscriber: ActorRef): Unit
  def unsubscribe(subscriber: ActorRef): Unit
}

class TracerExtensionImpl(system: ExtendedActorSystem) extends TracerExtension {
  private val _settings = TraceSettings(system)
  private val _metricsExtension = Metrics.get(system)

  private val _hostnamePrefix = Try(InetAddress.getLocalHost.getHostName).getOrElse("unknown-localhost")
  private val _tokenCounter = new AtomicLong
  private val _subscriptions = system.actorOf(Props[TraceSubscriptions], "trace-subscriptions")
  private val _incubator = system.actorOf(Incubator.props(_subscriptions))

  private def newToken: String =
    _hostnamePrefix + "-" + String.valueOf(_tokenCounter.incrementAndGet())

  def newContext(name: String): TraceContext =
    createTraceContext(name)

  def newContext(name: String, token: String): TraceContext =
    createTraceContext(name, token)

  def newContext(name: String, token: String, timestamp: RelativeNanoTimestamp, isOpen: Boolean, isLocal: Boolean): TraceContext =
    createTraceContext(name, token, timestamp, isOpen, isLocal)

  private def createTraceContext(traceName: String, token: String = newToken, startTimestamp: RelativeNanoTimestamp = RelativeNanoTimestamp.now,
    isOpen: Boolean = true, isLocal: Boolean = true): TraceContext = {

    def newMetricsOnlyContext = new MetricsOnlyContext(traceName, token, isOpen, _settings.levelOfDetail, startTimestamp, null, _metricsExtension, system)

    if (_settings.levelOfDetail == LevelOfDetail.MetricsOnly || !isLocal)
      newMetricsOnlyContext
    else {
      if (!_settings.sampler.shouldTrace)
        newMetricsOnlyContext
      else
        new TracingContext(traceName, token, true, _settings.levelOfDetail, isLocal, startTimestamp, null, _metricsExtension, this, system, dispatchTracingContext)
    }
  }

  def subscribe(subscriber: ActorRef): Unit = _subscriptions ! TraceSubscriptions.Subscribe(subscriber)
  def unsubscribe(subscriber: ActorRef): Unit = _subscriptions ! TraceSubscriptions.Unsubscribe(subscriber)

  private[kamon] def dispatchTracingContext(trace: TracingContext): Unit =
    if (_settings.sampler.shouldReport(trace.elapsedTime))
      if (trace.shouldIncubate)
        _incubator ! trace
      else
        _subscriptions ! trace.generateTraceInfo

}

case class TraceInfo(name: String, token: String, timestamp: NanoTimestamp, elapsedTime: NanoInterval, metadata: Map[String, String], segments: List[SegmentInfo])
case class SegmentInfo(name: String, category: String, library: String, timestamp: NanoTimestamp, elapsedTime: NanoInterval, metadata: Map[String, String])