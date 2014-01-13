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
package kamon.dashboard

import spray.routing.HttpService
import akka.actor._
import spray.routing.directives.LogEntry
import akka.event.Logging
import spray.http.MediaTypes._
import spray.httpx.SprayJsonSupport
import spray.http.HttpRequest
import akka.actor.OneForOneStrategy

class DashboardServiceActor extends Actor with DashboardService {

  def actorRefFactory = context
  def receive = runRoute(DashboardRoute)

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() { case _ ⇒ SupervisorStrategy.Stop }
}

trait DashboardService extends HttpService with StaticResources with DashboardPages with DashboardMetricsApi {

  def showPath(req: HttpRequest) = LogEntry(s"Method = ${req.method}, Path = ${req.uri}", Logging.InfoLevel)

  val DashboardRoute =
    logRequest(showPath _) {
      staticResources ~ dashboardPages //~ dashboardMetricsApi
    }
}

trait StaticResources extends HttpService {

  val staticResources = get { getFromResourceDirectory("web") }
}

trait DashboardPages extends HttpService {

  val dashboardPages =
    path("") {
      respondWithMediaType(`text/html`) {
        getFromResource("web/index.html")
      }
    }
}

trait DashboardMetricsApi extends HttpService with SprayJsonSupport {

  /*import scala.collection.JavaConverters._
  import kamon.metric.Metrics._
  import kamon.dashboard.protocol.DashboardProtocols._

  val metricFilter = new MetricFilter() {
    def matches(name: String, m:Metric) = {
      !name.contains("kamon") && name.contains("Mailbox/PROCESSINGTIME") && !name.contains("UnhandledMessageForwarder") && !name.contains("deadLetterListener")  && !name.contains("$DefaultLogger")
    }
  }

  def actorSystemMetrics = actorSystemNames.flatMap(name => actorSystem(name))
                                           .map(system => ActorSystemMetricsHolder(system.actorSystemName, system.dispatchers.map { case(name, metricCollector) => (name -> DispatcherMetricCollectorHolder(name, metricCollector.activeThreadCount.snapshot.median, metricCollector.poolSize.snapshot.median, metricCollector.queueSize.snapshot.median))}.toMap))

  val withTotalMessages = (dataHolders: Seq[TimerDataHolder]) => {
    val numberOfMessages = dataHolders.map(_.count).sum

    new TotalMessages(numberOfMessages, dataHolders.size, dataHolders)
  }

  def timerMetrics = registry.getTimers(metricFilter).asScala.map{ case(name, timer) => TimerDataHolder(name, timer.getMeanRate, timer.getSnapshot.get99thPercentile)}.toVector

  val dashboardMetricsApi =
      pathPrefix("metrics") {
        path("dispatchers") {
          get {
            complete (actorSystemMetrics)
          }
        } ~
        path("messages") {
          get {
            complete (withTotalMessages(timerMetrics))
          }
        } ~
        path("actorTree") {
          get {
            complete (ActorTree("/", ActorTree("Pang", ActorTree("Pang-children") :: Nil) :: ActorTree("Ping") :: ActorTree("Pong", ActorTree("Pong-children") :: Nil):: Nil))
          }
        }
      }*/
}