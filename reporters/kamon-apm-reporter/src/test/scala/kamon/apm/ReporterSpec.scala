package kamon.apm

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import kamon.Kamon
import kamon.metric.PeriodSnapshot
import kamon.tag.TagSet
import kamon.trace.{Identifier, Span, Trace}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import java.time.Instant
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


class ReporterSpec extends TestKit(ActorSystem("MetricReporterSpec"))
  with AnyWordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender with BeforeAndAfterEach with Eventually {

  var server: Future[ServerBinding] = null

  private var (helloCount, goodByeCount, ingestCount, tracingCount) = (0,0,0,0)

  override def beforeAll(): Unit = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val executionContext: ExecutionContext = system.dispatcher


    lazy val routes: Route = entity(as[Array[Byte]]) { buff =>
      post {
        pathPrefix("v2") {
          path("hello") {
            helloCount+=1
            testActor ! "hello"
            if(helloCount > 2) complete("hello") else complete(StatusCodes.InternalServerError)
          } ~
          path("goodbye") {
            goodByeCount+=1
            testActor ! "goodbye"
            if(goodByeCount > 2) complete("good") else complete(StatusCodes.InternalServerError)
          } ~
          path("metrics") {
            ingestCount+=1
            testActor ! "metrics"
            if(ingestCount > 2) complete("") else complete(StatusCodes.InternalServerError)
          } ~
          path("spans")  {
            tracingCount+=1
            testActor ! "spans"
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

  var reporter: KamonApm = null
  val emptySnapshot = PeriodSnapshot(
    Instant.EPOCH,
    Instant.EPOCH.plusSeconds(1),
    Seq.empty,
    Seq.empty,
    Seq.empty,
    Seq.empty,
    Seq.empty
  )

  "Metric reporter on flaky network" should {
    "retry initial HELLO" in {
      reporter = new KamonApm(ExecutionContext.global)
      expectMsg("hello")
      expectMsg("hello")
      expectMsg("hello")
      expectNoMsg(1 second)
    }

    "retry ingestion and then continue posting queued snapshots" in {
      val initialTimestamp = Kamon.clock().instant().minusSeconds(60)
      val nextTimestamp = initialTimestamp.plusMillis(100)
      val initialSnapshot = emptySnapshot.copy(from = initialTimestamp, to = nextTimestamp)
      val nextSnapshot = emptySnapshot.copy(from = nextTimestamp, to = nextTimestamp.plusMillis(100))

      reporter.reportPeriodSnapshot(initialSnapshot)
      reporter.reportPeriodSnapshot(nextSnapshot)

      expectMsg("metrics")
      expectMsg("metrics")
      expectMsg("metrics")
      expectMsg("metrics")
      expectNoMsg(1 second)
    }

    "retry span ingestion" in {
      val span = Span.Finished(Identifier.Empty, Trace.Empty, Identifier.Empty, "", false, false,
        Instant.now(), Instant.now(), Span.Kind.Unknown, Span.Position.Unknown,
        TagSet.Empty, TagSet.Empty, Seq.empty, Seq.empty)

      reporter.reportSpans(Seq(span))
      expectMsg("spans")
      expectMsg("spans")
      expectMsg("spans")

      expectNoMsg(1 second)
    }

    "don't retry lost Goodbye in order not to hang shutdown of host app" in {
      reporter.stop()
      expectMsg("goodbye")
      expectNoMsg(1 second)
    }
  }
}
