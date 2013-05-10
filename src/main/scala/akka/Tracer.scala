package akka

import actor.{Props, Actor, ActorSystemImpl}
import concurrent.forkjoin.ForkJoinPool
import scala.concurrent.duration._
import com.newrelic.api.agent.NewRelic
import akka.dispatch.Mailbox

object Tracer {
  var system: ActorSystemImpl = _
  var forkJoinPool:ForkJoinPool = _
  var mailbox:Mailbox = _

  def collectPool(pool: ForkJoinPool) = forkJoinPool = pool
  def collectActorSystem(actorSystem: ActorSystemImpl)  = system = actorSystem

  def collectMailBox(mb: akka.dispatch.Mailbox)  =  {
    mailbox = mb
  }

  def start():Unit ={
    implicit val dispatcher = system.dispatcher
    val metricsActor = system.actorOf(Props[MetricsActor], "PoolActor")

    system.scheduler.schedule(10 seconds, 6 second, metricsActor, PoolMetrics(forkJoinPool))
    system.scheduler.schedule(10 seconds, 6 second, metricsActor, MailboxMetrics(mailbox))
  }
}

case class PoolMetrics(poolName:String, data:Map[String,Int])
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
case class MailboxMetrics(mbName:String, mailBox:Mailbox)
object  MailboxMetrics {
  def apply(mb: Mailbox) = new MailboxMetrics(mb.actor.self.path.toString,mb)
}

class MetricsActor extends Actor {
    def receive = {
      case poolMetrics:PoolMetrics => {
        println(poolMetrics)
        poolMetrics.data.map{case(k,v) => NewRelic.recordMetric(s"${poolMetrics.poolName}:${k}",v)}
      }
      case mailboxMetrics:MailboxMetrics => {
        val actorName  = mailboxMetrics.mbName
        val mb = mailboxMetrics.mailBox
        println(s"Sending metrics to Newrelic MailBoxMonitor -> ${actorName}")


        NewRelic.recordMetric(s"${actorName}:Mailbox:NumberOfMessages",mb.numberOfMessages)
        NewRelic.recordMetric(s"${actorName}:Mailbox:MailboxDispatcherThroughput",mb.dispatcher.throughput)

        NewRelic.addCustomParameter(s"${actorName}:Mailbox:Status", mb.hasMessages.toString)
        NewRelic.addCustomParameter(s"${actorName}:Mailbox:HasMessages", mb.hasMessages.toString)
      }
    }
}