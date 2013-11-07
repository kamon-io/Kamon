package kamon

import _root_.spray.httpx.RequestBuilding
import _root_.spray.routing.SimpleRoutingApp
import akka.testkit.TestKit
import akka.actor.{ActorRef, ActorSystem}
import org.scalatest.WordSpecLike
import scala.concurrent.Await
import scala.concurrent.duration._
import _root_.spray.client.pipelining._
import akka.util.Timeout
import kamon.trace.Trace
import kamon.Kamon.Extension
import kamon.trace.UowTracing.{Finish, Start}

class ServerRequestTracingSpec extends TestKit(ActorSystem("server-request-tracing-spec")) with WordSpecLike with RequestBuilding with TestServer {

  "the spray server request tracing instrumentation" should {
    "trace a request start/finish sequence when proper TraceContext is received" in {
      send {
        Get(s"http://127.0.0.1:$port/ok")
      }

      within(5 seconds) {
        val traceId = expectMsgPF() { case Start(id, _) => id}
        expectMsgPF() { case Finish(traceId) => }
      }
    }

    "finish a request even if no TraceContext is received in the response" in {
      send {
        Get(s"http://127.0.0.1:$port/clearcontext")
      }

      within(5 seconds) {
        val traceId = expectMsgPF() { case Start(id, _) => id}
        expectMsgPF() { case Finish(traceId) => }
      }
    }

    "give a initial transaction name using the method and path from the request" in {
      send {
        Get(s"http://127.0.0.1:$port/accounts")
      }

      within(5 seconds) {
        expectMsgPF() { case Start(_, "GET: /accounts") => }
      }
    }
  }
}

trait TestServer extends SimpleRoutingApp {
  self: TestKit =>

  // Nasty, but very helpful for tests.
  AkkaExtensionSwap.swap(system, Trace, new Extension {
    def manager: ActorRef = testActor
  })

  implicit val timeout = Timeout(20 seconds)
  val port: Int = Await.result(
    startServer(interface = "127.0.0.1", port = 0)(
      get {
        path("ok") {
          complete("ok")
        } ~
          path("clearcontext"){
            complete {
              Trace.clear
              "ok"
            }
          }
      }
    ), timeout.duration).localAddress.getPort

  val send = sendReceive(system, system.dispatcher, timeout)

}
