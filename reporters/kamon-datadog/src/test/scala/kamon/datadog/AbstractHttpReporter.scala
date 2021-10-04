package kamon.datadog

import kamon.Kamon
import okhttp3.mockwebserver.{MockResponse, MockWebServer}
import org.scalatest.{BeforeAndAfterAll, WordSpec}

trait AbstractHttpReporter extends WordSpec with BeforeAndAfterAll {
  // Not happy about having this here instead of in the beforeAll (as it should be)
  // but doing otherwise would require bigger refactoring on the tests.
  Kamon.init()

  protected val server = new MockWebServer()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server.start()
  }

  protected def mockResponse(path: String, response: MockResponse): String = {
    server.enqueue(response)
    server.url(path).toString
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    server.shutdown()
    Kamon.stop()
  }

}
