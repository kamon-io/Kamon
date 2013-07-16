package kamon.instrumentation

import org.scalatest.{Matchers, WordSpec}
import akka.actor.ActorSystem
import kamon.Kamon

class ActorSystemInstrumentationSpec extends WordSpec with Matchers {

  // TODO: Selection filters to exclude unwanted actor systems. Read from configuration.

  "the actor system instrumentation" should {
    "register all actor systems created" in {
      val as1 = ActorSystem("as1")
      val as2 = ActorSystem("as2")


      Kamon.Metric.actorSystem("as1") should not be (None)
      Kamon.Metric.actorSystem("as2") should not be (None)
      Kamon.Metric.actorSystem("unknown") should be (None)
    }
  }
}
