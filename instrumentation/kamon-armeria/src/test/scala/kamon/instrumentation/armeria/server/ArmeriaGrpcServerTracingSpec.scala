package kamon.instrumentation.armeria.server

import com.linecorp.armeria.client.Clients
import com.linecorp.armeria.common.grpc.GrpcSerializationFormats
import com.linecorp.armeria.server.grpc.GrpcService
import kamon.armeria.instrumentation.grpc.GrpcExample.{ArmeriaHelloServiceGrpc, HelloRequest}
import kamon.tag.Lookups.{plain, plainLong}
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import utils.ArmeriaHelloGrpcService
import utils.ArmeriaServerSupport.startArmeriaServer

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class ArmeriaGrpcServerTracingSpec extends AnyWordSpec
  with Matchers
  with BeforeAndAfterAll
  with InitAndStopKamonAfterAll
  with Eventually
  with TestSpanReporter {

  val interface = "127.0.0.1"
  val httpPort = 8080

  private val helloService =
    Clients.newClient(s"gproto+http://$interface:$httpPort/", classOf[ArmeriaHelloServiceGrpc.ArmeriaHelloServiceBlockingStub])

  val grpcService: GrpcService =
    GrpcService.builder()
      .addService(ArmeriaHelloServiceGrpc.bindService(new ArmeriaHelloGrpcService, ExecutionContext.global))
      .supportedSerializationFormats(GrpcSerializationFormats.values)
      .enableUnframedRequests(true)
      .build()

  private val httpServer = startArmeriaServer(httpPort, grpcService = Some(grpcService))

  "An application exposing Armeria gRPC services" should {
    "generate Spans with the name of the service" in {
      val reply = helloService.hello(HelloRequest("Kamon"))

      reply.message shouldBe "Hello, Kamon!"

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "/kamon.armeria.instrumentation.grpc.ArmeriaHelloService/Hello"
        span.hasError shouldBe false
        span.metricTags.get(plain("component")) shouldBe "armeria.http.server"
        span.metricTags.get(plain("http.method")) shouldBe "POST"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200L
      }
    }
  }

  override protected def afterAll(): Unit = {
    httpServer.close()
    super.afterAll()
  }

}
