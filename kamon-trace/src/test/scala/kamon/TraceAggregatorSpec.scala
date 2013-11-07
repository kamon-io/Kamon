package kamon

import org.scalatest.{WordSpecLike, WordSpec}
import akka.testkit.{TestKitBase, TestKit}
import akka.actor.ActorSystem
import scala.concurrent.duration._
import kamon.trace.UowTracing.{Finish, Rename, Start}
import kamon.trace.{UowTrace, UowTraceAggregator}

class TraceAggregatorSpec extends TestKit(ActorSystem("TraceAggregatorSpec")) with WordSpecLike {

  "a TraceAggregator" should {
    "send a UowTrace message out after receiving a Finish message" in new AggregatorFixture {
      within(1 second) {
        aggregator ! Start(1, "/accounts")
        aggregator ! Finish(1)

        //expectMsg(UowTrace("UNKNOWN", Seq(Start(1, "/accounts"), Finish(1))))
      }
    }

    "change the uow name after receiving a Rename message" in new AggregatorFixture {
      within(1 second) {
        aggregator ! Start(1, "/accounts")
        aggregator ! Rename(1, "test-uow")
        aggregator ! Finish(1)

        //expectMsg(UowTrace("test-uow", Seq(Start(1, "/accounts"), Finish(1))))
      }
    }
  }


  trait AggregatorFixture {
    val aggregator = system.actorOf(UowTraceAggregator.props(testActor, 10 seconds))
  }
}
