package kamon.instrumentation.armeria.client

import com.linecorp.armeria.client.Clients
import com.linecorp.armeria.common.grpc.GrpcSerializationFormats
import com.linecorp.armeria.server.grpc.GrpcService
import kamon.Kamon
import kamon.armeria.instrumentation.grpc.GrpcExample.{ArmeriaHelloServiceGrpc, HelloRequest}
import kamon.context.Context
import kamon.tag.Lookups.{plain, plainBoolean, plainLong}
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import kamon.trace.Span
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import utils.ArmeriaHelloGrpcService
import utils.ArmeriaServerSupport.startArmeriaServer

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class ArmeriaGrpcClientTracingSpec extends AnyWordSpec
  with Matchers
  with BeforeAndAfterAll
  with InitAndStopKamonAfterAll
  with Eventually
  with TestSpanReporter
  with OptionValues {

  val interface = "127.0.0.1"
  val httpPort = 8080

  val grpcService: GrpcService =
    GrpcService.builder()
      .addService(ArmeriaHelloServiceGrpc.bindService(new ArmeriaHelloGrpcService, ExecutionContext.global))
      .supportedSerializationFormats(GrpcSerializationFormats.values)
      .enableUnframedRequests(true)
      .build()

  private val httpServer = startArmeriaServer(httpPort, grpcService = Some(grpcService))

  "The Armeria http client tracing instrumentation" should {

    "generate a span around an async grpc request" in {
      val helloServiceStr = "kamon.armeria.instrumentation.grpc.ArmeriaHelloService"

      val okSpan = Kamon.spanBuilder("ok-async-operation-span").start()
      val helloService = Clients.newClient(s"gproto+http://$interface:$httpPort/", classOf[ArmeriaHelloServiceGrpc.ArmeriaHelloServiceBlockingStub])

      val reply = Kamon.runWithContext(Context.of(Span.Key, okSpan)) {
        helloService.hello(HelloRequest("Kamon"))
      }

      reply.message shouldBe "Hello, Kamon!"

     eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "ArmeriaHelloService/Hello"
        span.kind shouldBe Span.Kind.Client
        span.metricTags.get(plain("component")) shouldBe "armeria.http.client"
        span.metricTags.get(plain("http.method")) shouldBe "POST"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200
        span.metricTags.get(plainBoolean("error")) shouldBe false
        span.tags.get(plain("http.url")) shouldBe s"http://$interface:$httpPort/$helloServiceStr/Hello"
        okSpan.id shouldBe span.parentId
      }
    }
  }

  override protected def afterAll(): Unit = {
    httpServer.close()
    super.afterAll()
  }
}
