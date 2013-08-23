package kamon.dashboard

import spray.routing.HttpService
import akka.actor._
import spray.routing.directives.LogEntry
import akka.event.Logging
import spray.http.MediaTypes._
import spray.json._
import spray.httpx.SprayJsonSupport
import kamon.Kamon
import spray.http.HttpRequest
import akka.actor.OneForOneStrategy
import com.codahale.metrics.{Metric, MetricFilter}


class DashboardServiceActor extends Actor with DashboardService {

  def actorRefFactory = context
  def receive = runRoute(DashboardRoute)

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() { case _ => SupervisorStrategy.Stop }
}

trait DashboardService extends HttpService with StaticResources with DashboardPages with DashboardMetricsApi {

  def showPath(req: HttpRequest) = LogEntry(s"Method = ${req.method}, Path = ${req.uri}", Logging.InfoLevel)

  val DashboardRoute =
    logRequest(showPath _) {
      staticResources ~ dashboardPages ~ dashboardMetricsApi
    }
}

trait StaticResources extends HttpService {

  val staticResources = get { getFromResourceDirectory("web")}
}

trait DashboardPages extends HttpService {

  val dashboardPages =
    path("") {
      respondWithMediaType(`text/html`) {
        getFromResource("web/index.html")
      }
    }
}

trait DashboardMetricsApi extends HttpService with SprayJsonSupport{

  case class TimerDataHolder(name:String, count:Long, percentile99:Double)
  case class TotalMessages(messages:Long, actors:Long, data:List[TimerDataHolder])
  case class DispatcherMetricCollectorHolder(name:String, activeThreadCount: Double, poolSize: Double, queueSize:Double)
  case class ActorSystemMetricsHolder(actorSystem:String, dispatchers:Map[String, DispatcherMetricCollectorHolder])

  object DashboardJsonProtocol extends DefaultJsonProtocol {
    implicit val TimerDataHolderJsonProtocol = jsonFormat3(TimerDataHolder)
    implicit val TotalMessagesJsonProtocol = jsonFormat3(TotalMessages)
    implicit val DispatcherMetricCollectorJsonProtocol = jsonFormat4(DispatcherMetricCollectorHolder)
    implicit val ActorSystemMetricsJsonProtocol = jsonFormat2(ActorSystemMetricsHolder)
  }

  import spray.httpx.marshalling._
  import Kamon.Metric._
  import scala.collection.JavaConverters._
  import kamon.metric.Metrics._
  import DashboardJsonProtocol._

  val metricFilter = new MetricFilter() {
    def matches(name: String, m:Metric) = {
      !name.contains("kamon") && name.contains("Mailbox/PROCESSINGTIME") && !name.contains("UnhandledMessageForwarder") && !name.contains("deadLetterListener")  && !name.contains("$DefaultLogger")
    }
  }

  def actorSystemMetrics = actorSystemNames.flatMap(name => actorSystem(name))
                                           .map(system => ActorSystemMetricsHolder(system.actorSystemName, system.dispatchers.map { case(name, metricCollector) => (name -> DispatcherMetricCollectorHolder(name, metricCollector.activeThreadCount.snapshot.median, metricCollector.poolSize.snapshot.median, metricCollector.queueSize.snapshot.median))}.toMap))

  val withTotalMessages = (dataHolders: List[TimerDataHolder]) => {
    val numberOfMessages = dataHolders.map(_.count).sum

    new TotalMessages(numberOfMessages,dataHolders.size, dataHolders)
  }

  def timerMetrics = registry.getTimers(metricFilter).asScala.map{ case(name, timer) => TimerDataHolder(name, timer.getCount, timer.getSnapshot.get99thPercentile())}.toList

  val dashboardMetricsApi =
      pathPrefix("metrics") {
        path("dispatchers") {
          get {
            complete {
              marshal(actorSystemMetrics)
            }
          }
        } ~
        path("messages") {
          get {
            complete {
              marshal(withTotalMessages(timerMetrics))
            }
          }
        }
      }
}