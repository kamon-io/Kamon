package akka

import actor.{Props, ActorSystemImpl}
import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.duration._
import akka.dispatch.Mailbox
import scala._
import com.newrelic.api.agent.NewRelic

object Tracer {
  protected[this] var mailboxes:List[Mailbox] = List.empty
  protected[this] var tracerActorSystem: ActorSystemImpl = _
  protected[this] var forkJoinPool:ForkJoinPool = _

  def collectPool(pool: ForkJoinPool) = forkJoinPool = pool
  def collectActorSystem(actorSystem: ActorSystemImpl)  = tracerActorSystem = actorSystem
  def collectMailbox(mb: akka.dispatch.Mailbox)  =  mailboxes ::= mb

  def start():Unit ={
    implicit val dispatcher = tracerActorSystem.dispatcher
    val poolMetricsActorSender = tracerActorSystem.actorOf(Props(new PoolMetricsActorSender(forkJoinPool)), "PoolMetricsActorSender")
    //val mailboxMetricsActorSender = tracerActorSystem.actorOf(Props(new MailboxSenderMetricsActor(mailboxes)), "MailboxMetricsActorSender")

    tracerActorSystem.scheduler.schedule(10 seconds, 6 second, poolMetricsActorSender, "SendPoolMetrics")

    tracerActorSystem.scheduler.schedule(10 seconds, 6 second, new Runnable {
      def run() {
        val mbm = MailboxMetrics(mailboxes)
        mbm.mailboxes.map { case(actorName,mb) => {
          println(s"Sending metrics to Newrelic MailBoxMonitor -> ${actorName}")
          NewRelic.recordMetric(s"${actorName}:Mailbox:NumberOfMessages",mb.numberOfMessages)
          NewRelic.recordMetric(s"${actorName}:Mailbox:MailboxDispatcherThroughput",mb.dispatcher.throughput)

          NewRelic.addCustomParameter(s"${actorName}:Mailbox:Status", mb.hasMessages.toString)
          NewRelic.addCustomParameter(s"${actorName}:Mailbox:HasMessages", mb.hasMessages.toString)
      }
        }
      }
    })

  }
    //tracerActorSystem.scheduler.schedule(10 seconds, 6 second, mailboxMetricsActorSender, "SendMailboxMetrics")
}