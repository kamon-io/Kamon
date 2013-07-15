package kamon.instrumentation

import org.scalatest.{Matchers, WordSpec}
import akka.actor.ActorSystem
import kamon.metric.MetricDirectory

class DispatcherInstrumentationSpec extends WordSpec with Matchers{


  "the dispatcher instrumentation" should {
    "instrument a dispatcher that belongs to a non-filtered actor system" in {

    }
  }


  trait SingleDispatcherActorSystem {
    val actorSystem = ActorSystem("single-dispatcher")
  }
}

