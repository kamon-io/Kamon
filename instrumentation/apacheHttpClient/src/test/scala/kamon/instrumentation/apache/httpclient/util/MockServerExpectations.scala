package kamon.instrumentation.apache.httpclient.util

import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpTemplate
import org.mockserver.model.HttpRequest.{request => mockRequest}
import org.mockserver.model.HttpResponse.{response => mockResponse}
import org.mockserver.model.Delay
import java.util.HashMap
import org.mockserver.model.Header
import org.slf4j.LoggerFactory
import org.mockserver.model.Parameters

class MockServerExpectations(private val host: String, private val port: Int) {
  private val _logger = LoggerFactory.getLogger(classOf[MockServerExpectations])

  private[util] def client: MockServerClient = new MockServerClient(host, port)

  def simpleGetPath: String = "/simple-get"
  def simpleGetExpectation: Unit = client.when(mockRequest().withMethod("GET").withPath(simpleGetPath)).respond(
    mockResponse().withBody("simple-get says hi!!")
  )

  def customOptNamePath: String = "/custom-operation-name"
  def customOptNameExpectation: Unit = client.when(
    mockRequest().withMethod("POST").withPath(customOptNamePath)
  ).respond(mockResponse().withBody("got posted"))

  def checkHeadersPath: String = "/check-headers?dummy=true&notso=false"
  def checkHeadersExpectation: Unit = client.when(
    mockRequest().withMethod("GET").withPath("/check-headers").withQueryStringParameter(
      "dummy",
      "true"
    ).withQueryStringParameter("notso", "false")
  ).respond(mockResponse().withBody("check your headers"))

  def test500Path: String = "/test-500-error"
  def test500Expectation: Unit = {
    client.when(mockRequest().withPath(test500Path)).respond(mockResponse().withStatusCode(500))
  }

  def failingResponseHandlerPath: String = "/failing-handler"
  def failingResponseHandlerExpectation: Unit = {
    client.when(mockRequest().withPath(failingResponseHandlerPath)).respond(mockResponse().withStatusCode(204))
  }
}
