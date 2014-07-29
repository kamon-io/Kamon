package kamon.spray

import akka.actor.ActorSystem
import akka.testkit.{ TestProbe, TestKitBase }
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.http.HttpServerMetrics
import kamon.metric._
import org.scalatest.concurrent.{ PatienceConfiguration, ScalaFutures }
import org.scalatest.{ Matchers, WordSpecLike }
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }
import spray.httpx.RequestBuilding

class SprayServerMetricsSpec extends TestKitBase with WordSpecLike with Matchers with RequestBuilding
    with ScalaFutures with PatienceConfiguration with TestServer {

  val collectionContext = CollectionContext(100)

  implicit lazy val system: ActorSystem = ActorSystem("spray-server-metrics-spec", ConfigFactory.parseString(
    """
      |akka {
      |  loglevel = ERROR
      |}
      |
      |kamon {
      |  metrics {
      |    tick-interval = 1 hour
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

  "the Spray Server metrics instrumentation" should {
    "record trace metrics for requests received" in {
      Kamon(Metrics)(system).register(TraceMetrics("GET: /record-trace-metrics"), TraceMetrics.Factory).get.collect(collectionContext)
      val (connection, server) = buildClientConnectionAndServer
      val client = TestProbe()

      for (repetition ← 1 to 10) {
        client.send(connection, Get("/record-trace-metrics"))
        server.expectMsgType[HttpRequest]
        server.reply(HttpResponse(entity = "ok"))
        client.expectMsgType[HttpResponse]
      }

      for (repetition ← 1 to 5) {
        client.send(connection, Get("/record-trace-metrics"))
        server.expectMsgType[HttpRequest]
        server.reply(HttpResponse(entity = "bad-request", status = StatusCodes.BadRequest))
        client.expectMsgType[HttpResponse]
      }

      val snapshot = Kamon(Metrics)(system).register(TraceMetrics("GET: /record-trace-metrics"), TraceMetrics.Factory).get.collect(collectionContext)
      snapshot.elapsedTime.numberOfMeasurements should be(15)
    }

    "record http serve metrics for all the requests" in {
      Kamon(Metrics)(system).register(HttpServerMetrics, HttpServerMetrics.Factory).get.collect(collectionContext)
      val (connection, server) = buildClientConnectionAndServer
      val client = TestProbe()

      for (repetition ← 1 to 10) {
        client.send(connection, Get("/record-http-metrics"))
        server.expectMsgType[HttpRequest]
        server.reply(HttpResponse(entity = "ok"))
        client.expectMsgType[HttpResponse]
      }

      for (repetition ← 1 to 5) {
        client.send(connection, Get("/record-http-metrics"))
        server.expectMsgType[HttpRequest]
        server.reply(HttpResponse(entity = "bad-request", status = StatusCodes.BadRequest))
        client.expectMsgType[HttpResponse]
      }

      val snapshot = Kamon(Metrics)(system).register(HttpServerMetrics, HttpServerMetrics.Factory).get.collect(collectionContext)
      snapshot.countsPerTraceAndStatusCode("GET: /record-http-metrics")("200").count should be(10)
      snapshot.countsPerTraceAndStatusCode("GET: /record-http-metrics")("400").count should be(5)
      snapshot.countsPerStatusCode("200").count should be(10)
      snapshot.countsPerStatusCode("400").count should be(5)
    }
  }
}
