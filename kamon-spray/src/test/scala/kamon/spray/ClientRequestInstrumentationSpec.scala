package kamon.spray

import akka.testkit.{TestKitBase, TestProbe, TestKit}
import akka.actor.ActorSystem
import org.scalatest.WordSpecLike
import spray.httpx.RequestBuilding
import kamon.Kamon
import kamon.metrics.{ TraceMetrics, Metrics }
import spray.http.{ HttpResponse, HttpRequest }
import kamon.metrics.Subscriptions.TickMetricSnapshot
import kamon.trace.TraceRecorder
import spray.can.client.ClientRequestInstrumentation
import com.typesafe.config.ConfigFactory

class ClientRequestInstrumentationSpec extends TestKitBase with WordSpecLike with RequestBuilding with TestServer {
  implicit lazy val system: ActorSystem = ActorSystem("server-request-tracing-spec", ConfigFactory.parseString(
    """
      |kamon {
      |  metrics {
      |    tick-interval = 1 second
      |
      |    filters = [
      |      {
      |        trace {
      |          includes = [ "*" ]
      |          excludes = []
      |        }
      |      }
      |    ]
      |  }
      |}
    """.stripMargin
  ))

  implicit def ec = system.dispatcher

  "the client instrumentation" should {
    "record record the elapsed time for a http request when using the Http manager directly" in {

      val (hostConnector, server) = buildServer(httpHostConnector)
      val client = TestProbe()

      val metricListener = TestProbe()
      Kamon(Metrics)(system).subscribe(TraceMetrics, "*", metricListener.ref, permanently = true)

      val testContext = TraceRecorder.withNewTraceContext("direct-to-http-manager-request") {
        client.send(hostConnector, Get("/direct-to-http-manager-request"))
        TraceRecorder.currentContext
      }
      server.expectMsgType[HttpRequest]
      server.reply(HttpResponse(entity = "ok"))
      client.expectMsgType[HttpResponse]

      testContext.map(_.finish(Map.empty))

      metricListener.fishForMessage() {
        case snapshot @ TickMetricSnapshot(_, _, metrics) ⇒
          metrics.filterKeys(_.name == "direct-to-http-manager-request").exists {
            case (group, snapshot) ⇒
              snapshot.metrics.filterKeys(id ⇒ id.name == "" && id.tag == ClientRequestInstrumentation.SprayTime).nonEmpty
          }
        case other ⇒ false
      }
    }
  }

}
