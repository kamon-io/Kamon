package kamon.instrumentation

import org.scalatest.{Matchers, WordSpec}
import akka.actor.ActorSystem
import kamon.metric.MetricDirectory

class DispatcherInstrumentationSpec extends WordSpec with Matchers{
  import MetricDirectory.dispatcherStats


  "the dispatcher instrumentation" should {
    "instrument a dispatcher that belongs to a non-filtered actor system" in {
      val defaultDispatcherStats = dispatcherStats("single-dispatcher", "akka.actor.default-dispatcher")

      defaultDispatcherStats should not be None

      //KamonMetrics.watch[Actor] named "ivan"

    }
  }


  trait SingleDispatcherActorSystem {
    val actorSystem = ActorSystem("single-dispatcher")
  }
}

