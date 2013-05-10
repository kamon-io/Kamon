package akka

import org.aspectj.lang.annotation.{Pointcut, Before, Aspect}
import scala.concurrent.duration._
import com.newrelic.api.agent.NewRelic

@Aspect("perthis(poolMonitor())")
class PoolMonitorAspect extends ActorSystemHolder {

  @Pointcut("execution(scala.concurrent.forkjoin.ForkJoinPool.new(..)) && !within(PoolMonitorAspect)")
  protected def poolMonitor:Unit = {}

  @Before("poolMonitor() && this(pool)")
  def beforePoolInstantiation(pool: scala.concurrent.forkjoin.ForkJoinPool) {
    actorSystem.scheduler.schedule(10 seconds, 6 second, new Runnable {
      def run() {
        val poolName = pool.getClass.getSimpleName

        println(s"Sending metrics to Newrelic PoolMonitor -> ${poolName}")
        PoolConverter.toMap(pool).map{case(k,v) => NewRelic.recordMetric(s"${poolName}:${k}",v)}
        }
      })
    }
}

object PoolConverter {
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
