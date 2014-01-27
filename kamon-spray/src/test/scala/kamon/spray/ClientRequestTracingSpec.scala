package kamon.spray

import akka.testkit.TestKit
import akka.actor.ActorSystem
import org.scalatest.WordSpecLike
import spray.httpx.RequestBuilding
import spray.client.pipelining._
import kamon.trace.{ UowTrace, Trace }
import scala.concurrent.Await

class ClientRequestTracingSpec extends TestKit(ActorSystem("server-request-tracing-spec")) with WordSpecLike with RequestBuilding with TestServer {
  implicit val ec = system.dispatcher

  "the client instrumentation" should {
    "record segments for a client http request" in {

      Trace.start("record-segments", None)(system)

      send {
        Get(s"http://127.0.0.1:$port/ok")

        // We don't care about the response, just make sure we finish the Trace after the response has been received.
      } map (rsp ⇒ Trace.finish())

      val trace = expectMsgType[UowTrace]
      println(trace.segments)
    }
  }

}
