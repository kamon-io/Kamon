package kamon.instrumentation.akka.grpc

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import kamon.tag.Lookups.plain
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class AkkaGrpcTracingSpec extends AnyWordSpec with InitAndStopKamonAfterAll with Matchers with Eventually
    with TestSpanReporter with OptionValues {

  implicit val system = ActorSystem("akka-grpc-instrumentation")
  implicit val ec = system.dispatcher

  val greeterService = GreeterServiceHandler(new GreeterServiceImpl())
  val serverBinding = Http()
    .newServerAt("127.0.0.1", 8598)
    .bind(greeterService)


  val client = GreeterServiceClient(GrpcClientSettings.connectToServiceAt("127.0.0.1", 8598).withTls(false))

  "the Akka gRPC instrumentation" should {
    "create spans for the server-side" in {
      client.sayHello(HelloRequest("kamon"))

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "helloworld.GreeterService/SayHello"
        span.metricTags.get(plain("component")) shouldBe "akka.grpc.server"
        span.metricTags.get(plain("rpc.system")) shouldBe "grpc"
        span.metricTags.get(plain("rpc.service")) shouldBe "helloworld.GreeterService"
        span.metricTags.get(plain("rpc.method")) shouldBe "SayHello"
      }
    }
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    enableFastSpanFlushing()
  }
}
