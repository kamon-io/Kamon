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

class ServerRequestTracingSpec extends TestKit(ActorSystem("server-request-tracing-spec")) with WordSpecLike with RequestBuilding {

  "the spray server request tracing instrumentation" should {
    "start tracing a request when entering the server and close it when responding" in new TestServer {
      client(Get(s"http://127.0.0.1:$port/"))

      within(5 seconds) {
        val traceId = expectMsgPF() { case Start(id) => id}
        expectMsgPF() { case Finish(traceId) => }
      }
    }
  }



  trait TestServer extends SimpleRoutingApp {

    // Nasty, but very helpful for tests.
    AkkaExtensionSwap.swap(system, Trace, new Extension {
      def manager: ActorRef = testActor
    })

    implicit val timeout = Timeout(20 seconds)
    val port: Int = Await.result(
        startServer(interface = "127.0.0.1", port = 0)(
          get {
            complete("ok")
          }
        ), timeout.duration).localAddress.getPort

    val client = sendReceive(system, system.dispatcher, timeout)

  }
}
