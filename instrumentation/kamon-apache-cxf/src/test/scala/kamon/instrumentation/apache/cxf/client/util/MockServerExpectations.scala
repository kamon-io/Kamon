package kamon.instrumentation.apache.cxf.client.util

import org.apache.cxf.interceptor.Interceptor
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.message.Message
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class MockServerExpectations(private val host: String, private val port: Int) {

  private val _logger = LoggerFactory.getLogger(classOf[MockServerExpectations])

  private[util] def client: MockServerClient = new MockServerClient(host, port)
  def endpoint = s"http://$host:$port"
  def simplePath: String = "/HelloWorldService"
  def customPath: String = "/CustomHelloWorldService"
  def test500Path: String = "/Test500HelloWorldService"

  def failingPath: String = "/FailingHelloWorldService"

  def simpleTarget = s"$endpoint$simplePath"
  def customTarget = s"$endpoint$customPath"
  def test500Target = s"$endpoint$test500Path"
  def failingTarget = s"$endpoint$failingPath"

  val simpleClient = new HelloWorldServiceClient(simpleTarget)

  val customClient = new HelloWorldServiceClient(customTarget)

  val test500Client = new HelloWorldServiceClient(test500Target)

  val failingClient =
    new HelloWorldServiceClient(address = failingTarget, inInterceptors = List(new FailingInterceptor))

  private val RESPONSE: String =
    "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><sayHelloResponse xmlns=\"http://util.client.cxf.apache.instrumentation.kamon/\">Hello!</sayHelloResponse></soap:Body></soap:Envelope>"

  def simpleExpectation(): Unit = client.when(
    request()
      .withMethod("POST")
      .withPath(simplePath)
  ).respond(
    response()
      .withStatusCode(200)
      .withBody(RESPONSE)
  )

  def customExpectation(): Unit = client.when(
    request()
      .withMethod("POST")
      .withPath(customPath)
  ).respond(
    response()
      .withStatusCode(200)
      .withBody(RESPONSE)
  )

  def test500Expectation(): Unit = client.when(
    request()
      .withMethod("POST")
      .withPath(test500Path)
  ).respond(
    response()
      .withStatusCode(500)
      .withBody(RESPONSE)
  )
  def failingExpectation(): Unit = client.when(
    request()
      .withMethod("POST")
      .withPath(failingPath)
  ).respond(
    response()
      .withStatusCode(200)
      .withBody(RESPONSE)
  )
}

class HelloWorldServiceClient(
  address: String,
  outInterceptors: List[Interceptor[Message]] = List.empty,
  inInterceptors: List[Interceptor[Message]] = List.empty
) {
  val client: HelloWorldService = HelloWorldServiceClientFactory.create(address, outInterceptors, inInterceptors)
  def sayHello(): String = client.sayHello()
}

object HelloWorldServiceClientFactory {
  def create(
    address: String,
    outInterceptors: List[Interceptor[Message]],
    inInterceptors: List[Interceptor[Message]]
  ): HelloWorldService = {
    val factory = new JaxWsProxyFactoryBean()
    factory.setServiceClass(classOf[HelloWorldService])
    factory.setAddress(address)
    factory.getInInterceptors.addAll(inInterceptors.asJava)
    factory.getOutInterceptors.addAll(outInterceptors.asJava)
    factory.create().asInstanceOf[HelloWorldService]
  }
}
