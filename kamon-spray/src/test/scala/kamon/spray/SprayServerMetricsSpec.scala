package kamon.spray

import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import kamon.testkit.BaseKamonSpec
import org.scalatest.concurrent.{ PatienceConfiguration, ScalaFutures }
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }
import spray.httpx.RequestBuilding

class SprayServerMetricsSpec extends BaseKamonSpec("spray-server-metrics-spec") with RequestBuilding with ScalaFutures
    with PatienceConfiguration with TestServer {

  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon.metric {
        |  tick-interval = 1 hour
        |}
        |
        |akka.loggers = ["akka.event.slf4j.Slf4jLogger"]
      """.stripMargin)

  "the Spray Server metrics instrumentation" should {
    "record trace metrics for processed requests" in {
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

      val snapshot = takeSnapshotOf("GET: /record-trace-metrics", "trace")
      snapshot.histogram("elapsed-time").get.numberOfMeasurements should be(15)
    }

    "record http server metrics for all the requests" in {
      val (connection, server) = buildClientConnectionAndServer
      val client = TestProbe()

      // Erase metrics recorder from previous tests.
      takeSnapshotOf("spray-server", "http-server")

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

      val snapshot = takeSnapshotOf("spray-server", "http-server")
      snapshot.counter("GET: /record-http-metrics_200").get.count should be(10)
      snapshot.counter("GET: /record-http-metrics_400").get.count should be(5)
      snapshot.counter("200").get.count should be(10)
      snapshot.counter("400").get.count should be(5)
    }
  }
}
