package kamon.instrumentation.finagle.client

import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections.twitter2ScalaFuture
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.tracing.TraceInitializerFilter
import com.twitter.finagle.{Filter, Http, ListeningServer, Service}
import com.twitter.{util => twitterutil}
import com.typesafe.config.Config
import kamon.Kamon
import kamon.instrumentation.http.HttpMessage
import kamon.tag.Lookups
import kamon.testkit.{InitAndStopKamonAfterAll, Reconfigure}
import kamon.trace.Span
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

class HttpClientOpsSpec extends AnyWordSpecLike with Matchers with Reconfigure with InitAndStopKamonAfterAll
    with BeforeAndAfterAll {
  override protected def beforeAll(): Unit = {
    super.beforeAll()
    applyConfig(HttpClientOpsSpec.Config)
  }

  "The Finagle Instrumentation" when {
    "tracing a successful request" should {
      lazy val (spans, request, serverRequest) = HttpClientOpsSpec.awaitRequest("/ok", 5.seconds)
      lazy val span = spans.head

      "initialize a span for each request" in {
        spans should have size 1
        span.operationName shouldBe "/ok" // defined by overridden name-generator
        span.metricTags.get(Lookups.plainBoolean("error")) shouldBe false
        span.metricTags.get(Lookups.plain("component")) shouldBe "finagle.http.client.request"
        span.metricTags.get(Lookups.plain("span.kind")) shouldBe "client"
      }

      "set request span tags" in {
        span.metricTags.get(Lookups.plain("http.method")) shouldBe "GET"
        span.tags.get(Lookups.plain("http.url")) shouldBe "/ok"
      }

      "set response span tags" in {
        span.metricTags.get(Lookups.plainLong("http.status_code")) shouldBe 200
      }

      "set marks for standard finagle annotations" in {
        span.marks.map(_.key) should contain allOf ("wire/recv", "wire/send", "client/send")
      }

      "add trace propagation headers to the request" in {
        val headers = request.headerMap
        headers.get("X-B3-TraceId") should not be empty
        headers.get("X-B3-SpanId") should not be empty
        headers("X-B3-Sampled").toInt shouldBe 1
      }

      "receive trace propagation headers on server side" in {
        request.headerMap("X-B3-TraceId") shouldBe serverRequest.headerMap("X-B3-TraceId")
        request.headerMap("X-B3-SpanId") shouldBe serverRequest.headerMap("X-B3-SpanId")
        request.headerMap("X-B3-Sampled") shouldBe serverRequest.headerMap("X-B3-Sampled")
      }
    }
    "tracing a failed response" should {
      lazy val (spans, _, _) = HttpClientOpsSpec.awaitRequest("/oops", 5.seconds)
      lazy val span = spans.head

      "set response span tags" in {
        span.metricTags.get(Lookups.plainLong("http.status_code")) shouldBe 400
      }

      "set error metric tags" in {
        span.hasError shouldBe true
        span.metricTags.get(Lookups.plainBoolean("error")) shouldBe true
        span.tags.get(Lookups.plain("error.message")) shouldBe "Error HTTP response code '400'"
      }
    }
  }
}

object HttpClientOpsSpec {
  private val Config =
    s"""
       |kamon {
       |  trace {
       |    tick-interval = 1 millisecond
       |    sampler = "always"
       |  }
       |  propagation.http.default.tags.entries.outgoing.span = "b3"
       |  instrumentation.http-client.default.tracing.operations.name-generator = "${PathOperationNameGenerator.getClass.getName}"
       |}""".stripMargin

  private class MockSpanReporter extends kamon.module.SpanReporter {
    val spansReported: Promise[Seq[Span.Finished]] = Promise[Seq[Span.Finished]]
    override def stop(): Unit = {}
    override def reconfigure(config: Config): Unit = {}
    override def reportSpans(spans: Seq[Span.Finished]): Unit = if (spans.nonEmpty) spansReported.success(spans)
  }

  private object PathOperationNameGenerator extends kamon.instrumentation.http.HttpOperationNameGenerator {
    override def name(request: HttpMessage.Request): Option[String] = Some(request.path)
  }

  private class MockFinagleServer {
    val requestReceived: Promise[Request] = Promise[Request]
    val server: ListeningServer = Http.server
      // TraceInitializerFilter will clear b3 headers before we can check them in the test.
      .withStack(_.remove(TraceInitializerFilter.role))
      .serve(
        ":*",
        new Service[Request, Response] {
          def apply(request: Request): twitterutil.Future[Response] = {
            requestReceived.success(request)
            request.path match {
              case "/ok"   => twitterutil.Future.value(Response())
              case "/oops" => twitterutil.Future.value(Response().statusCode(400))
            }
          }
        }
      )
    val port: Int = server.boundAddress.asInstanceOf[InetSocketAddress].getPort
    def close(): Future[Unit] = server.close().as[Future[Unit]]
  }

  private class FinagleClient(localServicePort: Int) {
    val requestHandled: Promise[Request] = Promise[Request]
    val service: Service[Request, Response] = {
      val testFilter = Filter.mk[Request, Response, Request, Response] { (req, svc) =>
        requestHandled.success(req)
        svc(req)
      }

      val svc = Http.client
        .withKamonTracing // this is the thing from HttpClientOps under test
        .newService("localhost:" + localServicePort, "test_client")

      testFilter.andThen(svc)
    }
    def makeRequest(path: String): Future[Response] = service(Request(path)).as[Future[Response]]
    def close(): Future[Unit] = service.close().as[Future[Unit]]
  }

  private def awaitRequest(path: String, timeout: FiniteDuration): (Seq[Span.Finished], Request, Request) = {
    val reporter = new MockSpanReporter()
    val registration = Kamon.addReporter("mock-span-reporter", reporter)
    val server = new MockFinagleServer
    val client = new FinagleClient(server.port)

    import scala.concurrent.ExecutionContext.Implicits.global
    try {
      val f = for {
        _ <- client.makeRequest(path)
        request <- client.requestHandled.future
        serverRequest <- server.requestReceived.future
        span <- reporter.spansReported.future
      } yield (span, request, serverRequest)
      Await.result(f, timeout)
    } finally {
      registration.cancel()
      val close = Future.sequence(Seq(client.close(), server.close()))
      Await.result(close, timeout)
    }
  }
}
