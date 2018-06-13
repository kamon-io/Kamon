package kamon.kamino

import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpecLike}

import scala.concurrent.{ExecutionContext, Future}
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.Directives.{pathPrefix, _}
import kamino.IngestionV1
import kamino.IngestionV1.{Goodbye, Hello, MetricBatch, SpanBatch}
import kamon.Kamon
import kamon.kamino.reporters.{KaminoMetricReporter, KaminoTracingReporter}
import kamon.metric.{MetricsSnapshot, PeriodSnapshot}
import kamon.trace.SpanContext
import kamon.trace.Span.FinishedSpan
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._


class ReporterSpec extends TestKit(ActorSystem("MetricReporterSpec")) with WordSpecLike
  with Matchers with BeforeAndAfterAll with ImplicitSender with BeforeAndAfterEach with Eventually {

  var server: Future[ServerBinding] = null

  private var (helloCount, goodByeCount, ingestCount, tracingCount) = (0,0,0,0)

  override def beforeAll(): Unit = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val executionContext: ExecutionContext = system.dispatcher


    lazy val routes: Route = entity(as[Array[Byte]]) { buff =>
      post {
        pathPrefix("v1") {
          path("hello") {
            helloCount+=1
            testActor ! Hello.parseFrom(buff)
            if(helloCount > 2) complete("") else complete(StatusCodes.InternalServerError)
          } ~ path("goodbye") {
            goodByeCount+=1
            testActor ! Goodbye.parseFrom(buff)
            if(goodByeCount > 2) complete("") else complete(StatusCodes.InternalServerError)
          } ~ path("ingest") {
            ingestCount+=1
            testActor ! MetricBatch.parseFrom(buff)
            if(ingestCount > 2) complete("") else complete(StatusCodes.InternalServerError)
          } ~ path("tracing" / "ingest")  {
            tracingCount+=1
            testActor ! SpanBatch.parseFrom(buff)
            if(tracingCount > 2) complete("") else complete(StatusCodes.InternalServerError)
          }
        }
      }
    }

    server = Http().bindAndHandle(routes, "localhost", 8080)
  }

  override def beforeEach() = {
    helloCount = 0
    goodByeCount = 0
    ingestCount = 0
  }

  override def afterAll(): Unit = server.flatMap(_.unbind())(system.dispatcher)

  val emptySnapshot = MetricsSnapshot(Seq.empty, Seq.empty, Seq.empty, Seq.empty)

  "Metric reporter on flaky network" should {

    val reporter = new KaminoMetricReporter(Some(IngestionV1.Plan.METRIC_ONLY))
    val tracingReporter = new KaminoTracingReporter()

    "retry initial HELLO" in {
      reporter.start()
      expectMsgType[Hello]
      expectMsgType[Hello]
      expectMsgType[Hello]
      expectNoMsg(1 second)
    }

    "retry ingestion and then continue posting queued snapshots" in {
      val initialTimestamp = Kamon.clock().instant().minusSeconds(60)
      val nextTimestamp = initialTimestamp.plusMillis(100)
      val initialSnapshot = PeriodSnapshot(initialTimestamp, nextTimestamp, emptySnapshot)
      val nextSnapshot = PeriodSnapshot(nextTimestamp, nextTimestamp.plusMillis(100), emptySnapshot)

      reporter.reportPeriodSnapshot(initialSnapshot)
      reporter.reportPeriodSnapshot(nextSnapshot)

      expectMsgType[MetricBatch].getInterval.getFrom should be (initialTimestamp.toEpochMilli)
      expectMsgType[MetricBatch].getInterval.getFrom should be (initialTimestamp.toEpochMilli)
      expectMsgType[MetricBatch].getInterval.getFrom should be (initialTimestamp.toEpochMilli)

      expectMsgType[MetricBatch].getInterval.getFrom should be (nextTimestamp.toEpochMilli)
      expectNoMsg(1 second)
    }

    "retry span ingestion" in {
      tracingReporter.start()
      val span = FinishedSpan(SpanContext.EmptySpanContext, "", Instant.ofEpochMilli(0), Instant.ofEpochMilli(0), Map.empty, Seq.empty)
      tracingReporter.reportSpans(Seq(span))

      expectMsgType[SpanBatch]
      expectMsgType[SpanBatch]
      expectMsgType[SpanBatch]

      expectNoMsg(1 second)
    }

    "don't retry lost Goodbye in order not to hang shutdown of host app" in {
      reporter.stop()
      expectMsgType[Goodbye]
      expectNoMsg(1 second)
    }
  }
}
