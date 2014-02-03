package kamon.spray

import akka.testkit.{ TestKitBase, TestProbe }
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
import spray.can.Http
import akka.pattern.pipe
import spray.client.pipelining
import scala.concurrent.duration._

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
    """.stripMargin))

  implicit def ec = system.dispatcher

  "the client instrumentation" should {
    "record the elapsed time for a http request when using the Http manager directly and tag it as SprayTime" in {

      val metricListener = TestProbe()
      Kamon(Metrics)(system).subscribe(TraceMetrics, "*", metricListener.ref, permanently = true)

      val (hostConnector, server) = buildSHostConnectorAndServer
      val client = TestProbe()

      // Initiate a request within the context of a trace
      val testContext = TraceRecorder.withNewTraceContext("direct-to-http-manager-request") {
        client.send(hostConnector, Get("/direct-to-http-manager-request"))
        TraceRecorder.currentContext
      }

      // Accept the connection at the server side
      server.expectMsgType[Http.Connected]
      server.reply(Http.Register(server.ref))

      // Receive the request and reply back
      server.expectMsgType[HttpRequest]
      server.reply(HttpResponse(entity = "ok"))
      client.expectMsgType[HttpResponse]

      // Finish the trace
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


    "record the elapsed time for a http request when using the pipelining sendReceive and tag it as UserTime" in {

      val metricListener = TestProbe()
      Kamon(Metrics)(system).subscribe(TraceMetrics, "*", metricListener.ref, permanently = true)


      val (hostConnector, server) = buildSHostConnectorAndServer
      val client = TestProbe()
      val pipeline = pipelining.sendReceive(hostConnector)(system.dispatcher, 10 seconds)

      // Initiate a request within the context of a trace
      val testContext = TraceRecorder.withNewTraceContext("pipelining-helper-request") {
        pipeline(Get("/pipelining-helper-request")) to client.ref
        TraceRecorder.currentContext
      }

      // Accept the connection at the server side
      server.expectMsgType[Http.Connected]
      server.reply(Http.Register(server.ref))

      // Receive the request and reply back
      server.expectMsgType[HttpRequest]
      server.reply(HttpResponse(entity = "ok"))
      client.expectMsgType[HttpResponse]

      // Finish the trace
      testContext.map(_.finish(Map.empty))

      metricListener.fishForMessage() {
        case snapshot @ TickMetricSnapshot(_, _, metrics) ⇒
          metrics.filterKeys(_.name == "pipelining-helper-request").exists {
            case (group, snapshot) ⇒
              snapshot.metrics.filterKeys(id ⇒ id.name == "" && id.tag == ClientRequestInstrumentation.UserTime).nonEmpty
          }
        case other ⇒ false
      }
    }
  }

}
