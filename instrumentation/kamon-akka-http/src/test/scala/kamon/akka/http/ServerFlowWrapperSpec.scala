package kamon.akka.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import kamon.instrumentation.akka.http.ServerFlowWrapper
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}

class ServerFlowWrapperSpec extends WordSpecLike with Matchers with ScalaFutures {

  implicit private val system = ActorSystem("http-client-instrumentation-spec")
  implicit private val executor = system.dispatcher
  implicit private val materializer = ActorMaterializer()

  private val okReturningFlow = Flow[HttpRequest].map { _ =>
    HttpResponse(status = StatusCodes.OK, entity = HttpEntity("OK"))
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
  }
}
