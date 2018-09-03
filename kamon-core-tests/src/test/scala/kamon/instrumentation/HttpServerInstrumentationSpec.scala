package kamon.instrumentation

import kamon.context.Context
import kamon.testkit.SpanInspection
import org.scalatest.{Matchers, OptionValues, WordSpec}

import scala.collection.mutable

class HttpServerInstrumentationSpec extends WordSpec with Matchers with SpanInspection with OptionValues {

  "the HTTP server instrumentation" when {
    "configured for context propagation" should {
      "read context entries and tags from the incoming request" in {
        val httpServer = HttpServer.from("default", port = 8080, "http.server")
        val handler = httpServer.handle(fakeRequest("http://localhost:8080/", "/", "GET", Map(
          "context-tags" -> "tag=value;none=0011223344556677;",
          "custom-trace-id" -> "0011223344556677"
        )))

        handler.context.tags should contain only(
          "tag" -> "value",
          "none" -> "0011223344556677"
        )
      }

      "use the configured HTTP propagation channel" in {
        val httpServer = HttpServer.from("default", port = 8080, "http.server")
        val handler = httpServer.handle(fakeRequest("http://localhost:8080/", "/", "GET", Map(
          "context-tags" -> "tag=value;none=0011223344556677;",
          "custom-trace-id" -> "0011223344556677"
        )))

        handler.context.tags should contain only(
          "tag" -> "value",
          "none" -> "0011223344556677"
        )

        val span = inspect(handler.span)
        span.context().traceID.string shouldNot be("0011223344556677")
        span.tag("http.method").value shouldBe "GET"
        span.tag("http.url").value shouldBe "http://localhost:8080/"

        val responseHeaders = mutable.Map.empty[String, String]
        handler.startResponse(fakeResponse(200, responseHeaders), handler.context.withTag("hello", "world"))

      }
    }

    "when all capabilities are disabled" should {
      "not read any context from the incoming requests" in {
        val httpServer = HttpServer.from("noop", port = 8081, "http.server")
        val handler = httpServer.handle(fakeRequest("http://localhost:8080/", "/", "GET", Map(
          "context-tags" -> "tag=value;none=0011223344556677;",
          "custom-trace-id" -> "0011223344556677"
        )))

        handler.context shouldBe Context.Empty
      }

      "not create any span to represent the server request" in {
        val httpServer = HttpServer.from("noop", port = 8081, "http.server")
        val handler = httpServer.handle(fakeRequest("http://localhost:8080/", "/", "GET", Map(
          "context-tags" -> "tag=value;none=0011223344556677;",
          "custom-trace-id" -> "0011223344556677"
        )))

        handler.span.isEmpty() shouldBe true
      }

      "not record any HTTP server metrics" is (pending)
    }
  }

  def fakeRequest(requestUrl: String, requestPath: String, requestMethod: String, headers: Map[String, String]): HttpRequest =
    new HttpRequest {
      override def url: String = requestUrl
      override def path: String = requestPath
      override def method: String = requestMethod
      override def readHeader(header: String): Option[String] = headers.get(header)
    }

  def fakeResponse(responseStatusCode: Int, headers: mutable.Map[String, String]): HttpResponse.Writable[HttpResponse] =
    new HttpResponse.Writable[HttpResponse] {
      override def statusCode: Int = responseStatusCode
      override def writeHeader(header: String, value: String): Unit = headers.put(header, value)
      override def build(): HttpResponse = this
    }
}
