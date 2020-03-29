package kamon.datadog

import okhttp3.mockwebserver.{ MockResponse, MockWebServer }
import org.scalatest.{ BeforeAndAfterAll, WordSpec }

abstract class AbstractHttpReporter extends WordSpec with BeforeAndAfterAll {

  protected val server = new MockWebServer()

  override def beforeAll(): Unit = {
    server.start()
    super.beforeAll()
  }

  protected def mockResponse(path: String, response: MockResponse): String = {
    server.enqueue(response)
    server.url(path).toString
  }

  override def afterAll(): Unit = {
    server.shutdown()
    super.afterAll()
  }

}
