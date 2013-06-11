package akka

import org.aspectj.lang.annotation._
import akka.dispatch.MonitorableThreadFactory
import kamon.metric.Metrics
import scala.concurrent.forkjoin.ForkJoinPool
import com.codahale.metrics.Gauge

@Aspect("perthis(poolMonitor(scala.concurrent.forkjoin.ForkJoinPool))")
class PoolMonitorAspect {
  println("Created PoolMonitorAspect")


  @Pointcut("execution(scala.concurrent.forkjoin.ForkJoinPool.new(..)) && this(pool)")
  protected def poolMonitor(pool: scala.concurrent.forkjoin.ForkJoinPool):Unit = {}

  @After("poolMonitor(pool)")
  def beforePoolInstantiation(pool: scala.concurrent.forkjoin.ForkJoinPool):Unit = {
    pool.getFactory match {
      case m: MonitorableThreadFactory => registerForMonitoring(pool, m.name)
    }
  }

  def registerForMonitoring(fjp: ForkJoinPool, name: String) {
    Metrics.registry.register(s"/metrics/actorsystem/{actorsystem-name}/dispatcher/$name",
      new Gauge[Long] {
        def getValue: Long = fjp.getPoolSize
      })
  }
}
