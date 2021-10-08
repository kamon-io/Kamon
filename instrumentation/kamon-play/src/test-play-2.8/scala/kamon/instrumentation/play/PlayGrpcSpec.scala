package kamon.instrumentation.play

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings

import javax.inject.Inject
import kamon.instrumentation.play.grpc.{AbstractReplyServiceRouter, HelloRequest, HelloResponse, ReplyServiceClient, ReplyServiceClientProvider}
import kamon.tag.Lookups.{plain, plainLong}
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.time.SpanSugar
import org.scalatestplus.play.guice.GuiceOneServerPerTest
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.routing.Router
import play.grpc.scalatest.ServerGrpcClient

import scala.concurrent.Future

class ReplyServiceRouter @Inject()(implicit system: ActorSystem) extends AbstractReplyServiceRouter(system) {
  override def sayHello(in: HelloRequest): Future[HelloResponse] =
    Future.successful(HelloResponse(s"Hello, ${in.name}!"))
}


class PlayGrpcSpec extends PlaySpecShim with GuiceOneServerPerTest with ServerGrpcClient with InitAndStopKamonAfterAll
  with ScalaFutures with IntegrationPatience with TestSpanReporter with Eventually with SpanSugar {

  System.setProperty("config.file", System.getProperty("user.dir") + "/instrumentation/kamon-play/src/test-common/resources/conf/application-play-grpc.conf")

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(bind[Router].to[ReplyServiceRouter])
      .build()

  implicit def system: ActorSystem = app.injector.instanceOf(classOf[ActorSystem])
  implicit def client = ReplyServiceClient(GrpcClientSettings.connectToServiceAt("localhost", port).withTls(false))

  "An application exposing Play gRPC services" must {
    "generate Spans with the name of the service" in {
      client.sayHello(HelloRequest("Kamon")).futureValue

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName mustBe "/ReplyService/SayHello"
        span.hasError mustBe false
        span.metricTags.get(plain("component")) mustBe "play.server.akka-http"
        span.metricTags.get(plain("http.method")) mustBe "POST"
        span.metricTags.get(plainLong("http.status_code")) mustBe 200L
      }
    }
  }
}