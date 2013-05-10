package akka

import actor.{Props, Actor, ActorSystemImpl}
import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.duration._
import com.newrelic.api.agent.NewRelic
import akka.dispatch.Mailbox
import scala._

object Tracer {
  protected[this] var mailboxes:List[Mailbox] = List.empty
  protected[this] var tracerActorSystem: ActorSystemImpl = _
  protected[this] var forkJoinPool:ForkJoinPool = _

  def collectPool(pool: ForkJoinPool) = forkJoinPool = pool
  def collectActorSystem(actorSystem: ActorSystemImpl)  = tracerActorSystem = actorSystem
  def collectMailbox(mb: akka.dispatch.Mailbox)  =  mailboxes ::= mb

  def start():Unit ={
    implicit val dispatcher = tracerActorSystem.dispatcher
    val metricsActor = tracerActorSystem.actorOf(Props[MetricsActor], "MetricsActor")

    tracerActorSystem.scheduler.schedule(10 seconds, 6 second, metricsActor, PoolMetrics(forkJoinPool))
    tracerActorSystem.scheduler.schedule(10 seconds, 6 second, metricsActor, MailboxMetrics(mailboxes))
  }
}

case class PoolMetrics(poolName:String, data:Map[String,Int])
case class MailboxMetrics(mailboxes:Map[String,Mailbox])


object PoolMetrics {
  def apply(pool: ForkJoinPool) = new PoolMetrics(pool.getClass.getSimpleName, toMap(pool))

  def toMap(pool: scala.concurrent.forkjoin.ForkJoinPool):Map[String,Int] = Map[String,Int](
    "ActiveThreadCount" -> pool.getActiveThreadCount,
    "Parallelism" -> pool.getParallelism,
    "PoolSize" -> pool.getPoolSize,
    "QueuedSubmissionCount" -> pool.getQueuedSubmissionCount,
    "StealCount" -> pool.getStealCount.toInt,
    "QueuedTaskCount" -> pool.getQueuedTaskCount.toInt,
    "RunningThreadCount" -> pool.getRunningThreadCount
  )
}

object  MailboxMetrics {
  def apply(mailboxes: List[Mailbox]) = {
    new MailboxMetrics(mailboxes.take(mailboxes.length - 1).map{m => (m.actor.self.path.toString -> m)}.toMap) //TODO:reseach why collect an ActorSystemImpl
  }
}

class MetricsActor extends Actor {
    def receive = {

      case poolMetrics:PoolMetrics => {
        println(poolMetrics)
        poolMetrics.data.map{case(k,v) => NewRelic.recordMetric(s"${poolMetrics.poolName}:${k}",v)}
      }
      case mailboxMetrics:MailboxMetrics => {
        mailboxMetrics.mailboxes.map { case(actorName,mb) =>
          println(s"Sending metrics to Newrelic MailBoxMonitor -> ${actorName}")

          NewRelic.recordMetric(s"${actorName}:Mailbox:NumberOfMessages",mb.numberOfMessages)
          NewRelic.recordMetric(s"${actorName}:Mailbox:MailboxDispatcherThroughput",mb.dispatcher.throughput)

          NewRelic.addCustomParameter(s"${actorName}:Mailbox:Status", mb.hasMessages.toString)
          NewRelic.addCustomParameter(s"${actorName}:Mailbox:HasMessages", mb.hasMessages.toString)
        }
      }
    }
}