package akka

import scala.concurrent.forkjoin.ForkJoinPool
import akka.actor.Actor
import com.newrelic.api.agent.NewRelic

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

class PoolMetricsActorSender(forkJoinPool:ForkJoinPool) extends Actor {
  def receive = {
    case "SendPoolMetrics" => {
      val pool = PoolMetrics(forkJoinPool)
      println(s"Sending Metrics to NewRelic -> ${pool}")
      pool.data.map{case(k,v) => NewRelic.recordMetric(s"${pool.poolName}:${k}",v)}
    }
  }
}

