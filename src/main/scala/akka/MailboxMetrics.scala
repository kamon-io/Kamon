package akka

import akka.dispatch.Mailbox
import akka.actor.Actor
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

class MailboxSenderMetricsActor(mailboxes: List[Mailbox]) extends Actor {
  def receive = {
    case "SendMailboxMetrics" => {
        val mbm = MailboxMetrics(mailboxes)
        mbm.mailboxes.map { case(actorName, mb) => {
          println(s"Sending metrics to Newrelic MailBoxMonitor -> ${actorName}")
          NewRelic.recordMetric(s"${actorName}:Mailbox:NumberOfMessages", mb.numberOfMessages)
          NewRelic.recordMetric(s"${actorName}:Mailbox:MailboxDispatcherThroughput", mb.dispatcher.throughput)
          NewRelic.recordMetric(s"${actorName}:Mailbox:SuspendCount", mb.suspendCount)
        }
      }
    }
  }
}

