/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.newrelic

import akka.actor._
import scala.collection.mutable
import kamon.Kamon
import kamon.trace.{ UowTrace, Trace }
import kamon.newrelic.NewRelicMetric.{ MetricBatch, FlushMetrics }
import scala.concurrent.duration._

class NewRelic extends ExtensionId[NewRelicExtension] {
  def createExtension(system: ExtendedActorSystem): NewRelicExtension = new NewRelicExtension(system)
}

class NewRelicExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val api: ActorRef = system.actorOf(Props[NewRelicManager], "kamon-newrelic")
}

class NewRelicManager extends Actor with ActorLogging {
  log.info("Registering the Kamon(NewRelic) extension")

  Kamon(Trace)(context.system).api ! Trace.Register

  val webTransactionMetrics = context.actorOf(Props[WebTransactionMetrics], "web-transaction-metrics")
  val agent = context.actorOf(Props[Agent], "agent")

  import context.dispatcher
  context.system.scheduler.schedule(1 minute, 1 minute) {
    webTransactionMetrics.tell(FlushMetrics, agent)
  }

  def receive = {
    case trace: UowTrace ⇒ webTransactionMetrics ! trace
  }
}

object NewRelicMetric {
  case class ID(name: String, scope: Option[String])
  case class Data(var callCount: Long, var total: Double, var totalExclusive: Double, var min: Double, var max: Double, var sumOfSquares: Double) {
    def record(value: Double): Unit = {
      if (value > max) max = value
      if (value < min) min = value

      total += value
      totalExclusive += value
      sumOfSquares += value * value
      callCount += 1
    }
  }

  object Data {
    def apply(): Data = Data(0, 0, 0, Double.MaxValue, 0, 0)
  }

  case object FlushMetrics
  case class MetricBatch(metrics: List[(ID, Data)])
}

class WebTransactionMetrics extends Actor with ActorLogging {
  val apdexT = 0.5D
  var metrics = mutable.Map.empty[NewRelicMetric.ID, NewRelicMetric.Data]
  var apdex = NewRelicMetric.Data(0, 0, 0, apdexT, apdexT, 0)

  def receive = {
    case trace: UowTrace ⇒ updateStats(trace)
    case FlushMetrics    ⇒ flush
  }

  def flush: Unit = {
    sender ! MetricBatch(metrics.toList :+ (NewRelicMetric.ID("Apdex", None), apdex))
    apdex = NewRelicMetric.Data(0, 0, 0, apdexT, apdexT, 0)
    metrics = mutable.Map.empty[NewRelicMetric.ID, NewRelicMetric.Data]
  }

  def recordValue(metricID: NewRelicMetric.ID, value: Double): Unit = {
    metrics.getOrElseUpdate(metricID, NewRelicMetric.Data()).record(value)
  }

  def recordApdex(time: Double): Unit = {
    if (time <= apdexT)
      apdex.callCount += 1
    else if (time > apdexT && time <= (4 * apdexT))
      apdex.total += 1
    else
      apdex.totalExclusive += 1

  }

  def updateStats(trace: UowTrace): Unit = {
    // Basic Metrics
    val elapsedSeconds = trace.elapsed / 1E9D

    recordApdex(elapsedSeconds)
    recordValue(NewRelicMetric.ID("WebTransaction", None), elapsedSeconds)
    recordValue(NewRelicMetric.ID("HttpDispatcher", None), elapsedSeconds)
    recordValue(NewRelicMetric.ID("WebTransaction/Custom/" + trace.name, None), elapsedSeconds)
  }
}
