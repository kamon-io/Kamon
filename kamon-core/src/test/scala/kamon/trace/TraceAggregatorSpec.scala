package kamon.trace

import org.scalatest.{WordSpecLike, WordSpec}
import akka.testkit.{TestKitBase, TestKit}
import akka.actor.ActorSystem
import scala.concurrent.duration._
import kamon.trace.UowTracing.{Finish, Rename, Start}

class TraceAggregatorSpec extends TestKit(ActorSystem("TraceAggregatorSpec")) with WordSpecLike {

  "a TraceAggregator" should {
    "send a UowTrace message out after receiving a Finish message" in new AggregatorFixture {
      within(1 second) {
        aggregator ! Start()
        aggregator ! Finish()

        expectMsg(UowTrace("UNKNOWN", Seq(Start(), Finish())))
      }
    }

    "change the uow name after receiving a Rename message" in new AggregatorFixture {
      within(1 second) {
        aggregator ! Start()
        aggregator ! Rename("test-uow")
        aggregator ! Finish()

        expectMsg(UowTrace("test-uow", Seq(Start(), Finish())))
      }
    }
  }


  trait AggregatorFixture {
    val aggregator = system.actorOf(UowTraceAggregator.props(testActor, 10 seconds))
  }
}
