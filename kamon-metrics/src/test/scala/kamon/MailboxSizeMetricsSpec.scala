package kamon

import org.scalatest.{WordSpecLike, WordSpec}
import akka.testkit.TestKit
import akka.actor.{Props, ActorSystem}

class MailboxSizeMetricsSpec extends TestKit(ActorSystem("mailbox-size-metrics-spec")) with WordSpecLike {

  "the mailbox size metrics instrumentation" should {
    "register a counter for mailbox size upon actor creation" in {
      val target = system.actorOf(Props.empty, "sample")

      Metrics.registry.getHistograms.get("akka://mailbox-size-metrics-spec/sample:MAILBOX")
    }
  }
}
