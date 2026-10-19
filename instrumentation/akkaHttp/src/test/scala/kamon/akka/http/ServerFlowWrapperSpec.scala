package kamon.akka.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import kamon.instrumentation.akka.http.ServerFlowWrapper
import kamon.testkit.InitAndStopKamonAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContextExecutor

class ServerFlowWrapperSpec extends AnyWordSpecLike with Matchers with ScalaFutures with InitAndStopKamonAfterAll {

  implicit private val system: ActorSystem = ActorSystem("http-client-instrumentation-spec")
  implicit private val executor: ExecutionContextExecutor = system.dispatcher
  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  private val okReturningFlow = Flow[HttpRequest].map { _ =>
    HttpResponse(status = StatusCodes.OK, entity = HttpEntity("OK"))
  }

  private val defaultReturningFlow = Flow[HttpRequest].map { _ =>
    HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity.Default(
        ContentTypes.`text/plain(UTF-8)`,
        2,
        Source.single(ByteString.apply("OK"))
      )
    )
  }

  "the server flow wrapper" should {
    "keep strict entities strict" in {
      val flow = ServerFlowWrapper(okReturningFlow, "localhost", 8080)
      val request = HttpRequest()
      val response = Source.single(request)
        .via(flow)
        .runWith(Sink.head)
        .futureValue

      response.entity should matchPattern {
        case HttpEntity.Strict(_, _) =>
      }
    }

    "keep default entities default" in {
      val flow = ServerFlowWrapper(defaultReturningFlow, "localhost", 8081)
      val request = HttpRequest()
      val response = Source.single(request)
        .via(flow)
        .runWith(Sink.head)
        .futureValue

      response.entity should matchPattern {
        case _: HttpEntity.Default =>
      }
    }
  }
}
