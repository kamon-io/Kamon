package akka

import akka.dispatch.Mailbox
import com.newrelic.api.agent.NewRelic

case class MailboxMetrics(mailboxes:Map[String,Mailbox])


object  MailboxMetrics {
  def apply(mailboxes: List[Mailbox]) = {
    new MailboxMetrics(mailboxes.take(mailboxes.length - 1).map{m => (m.actor.self.path.toString -> m)}.toMap) //TODO:research why collect an ActorSystemImpl
  }

  def toMap(mb: Mailbox):Map[String,Int] = Map[String,Int](
    "NumberOfMessages" -> mb.numberOfMessages,
    "MailboxDispatcherThroughput" -> mb.dispatcher.throughput,
    "SuspendCount" -> mb.suspendCount
  )
}

class MailboxSenderMetrics(mailboxes:List[Mailbox]) extends Runnable {
  def run() {
    val mbm = MailboxMetrics(mailboxes)
    mbm.mailboxes.map { case(actorName,mb) => {
      println(s"Sending metrics to Newrelic MailBoxMonitor for Actor -> ${actorName}")

      MailboxMetrics.toMap(mb).map {case(property, value) =>
          NewRelic.recordMetric(s"${actorName}:Mailbox:${property}", value)
      }
    }
  }
 }
}


