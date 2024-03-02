package kamon.instrumentation

import kamon.tag.Lookups.{plain, plainLong}
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.Waiters.timeout
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import testapp.TestApp

import scala.concurrent.duration.DurationInt
import scala.util.Try

class SpringMVCInstrumentationSpec
    extends AnyWordSpec
    with Matchers
    with InitAndStopKamonAfterAll
    with TestSpanReporter {
  val port = "8080"
  val baseUrl = s"http://localhost:${port}"
  val client = new OkHttpClient()

  override def beforeAll(): Unit = {
    super.beforeAll()
    TestApp.main(Array(port))
  }

  "SpringMVC instrumentation" should {
    "create a span when receiving a request" in {
      executeGetRequest(s"${baseUrl}/employees")

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "/employees"
        span.metricTags.get(plain("component")) shouldBe "spring.server"
      }
    }

    "create span that does not contain path variables" in {
      createUser(s"${baseUrl}/employees").isSuccess shouldBe true
      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "/employees"
        span.metricTags.get(plain("component")) shouldBe "spring.server"
        span.metricTags.get(plain("http.method")) shouldBe "POST"
      }

      executeGetRequest(s"${baseUrl}/employees/1/foo/bar")
      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "/employees/{id}/foo/{test}"
        span.metricTags.get(plain("component")) shouldBe "spring.server"
        span.tags.get(plain("http.url")) shouldBe "http://localhost:8080/employees/1/foo/bar"
      }
    }

    "mark span as failed when throwing unchecked exception" in {
      executeGetRequest(s"${baseUrl}/throwIO")

      eventually(timeout(2.seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.hasError shouldBe true
        span.tags.get(plain("error.stacktrace")) should not be empty
      }
    }
  }

  private def executeGetRequest(url: String) = {
    val request = new Request.Builder()
      .url(url).build

    Try(client.newCall(request).execute)
  }

  private def createUser(url: String) = {
    val request = new Request.Builder()
      .url(url)
      .post(RequestBody
        .create(MediaType.get("application/json; charset=utf-8"), """{"name": "Simone", "role": "Dev"}"""))
      .build

    Try(client.newCall(request).execute)
  }
}
