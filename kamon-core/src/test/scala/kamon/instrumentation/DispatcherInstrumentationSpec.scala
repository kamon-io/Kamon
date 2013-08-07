package kamon.instrumentation

import org.scalatest.{Matchers, WordSpec}
import akka.actor.{Actor, Props, ActorSystem}
import kamon.metric.MetricDirectory
import kamon.Kamon

class DispatcherInstrumentationSpec extends WordSpec with Matchers{


  "the dispatcher instrumentation" should {
    "instrument a dispatcher that belongs to a non-filtered actor system" in new SingleDispatcherActorSystem {
      val x = Kamon.Metric.actorSystem("single-dispatcher").get.dispatchers
      (1 to 10).foreach(actor ! _)

      val active = x.get("akka.actor.default-dispatcher").get.activeThreadCount.snapshot
      println("Active max: "+active.max)
      println("Active min: "+active.min)

    }
  }


  trait SingleDispatcherActorSystem {
    val actorSystem = ActorSystem("single-dispatcher")
    val actor = actorSystem.actorOf(Props(new Actor {
      def receive = {
        case a => sender ! a;
      }
    }))

  }
}

