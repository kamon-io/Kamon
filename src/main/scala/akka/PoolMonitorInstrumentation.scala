package akka

import org.aspectj.lang.annotation._

@Aspect("perthis(poolMonitor(*))")
class PoolMonitorAspect {
  println("Created PoolMonitorAspect")

  @Pointcut("execution(scala.concurrent.forkjoin.ForkJoinPool.new(..)) && this(pool)")
  protected def poolMonitor(pool:scala.concurrent.forkjoin.ForkJoinPool):Unit = {}

  @After("poolMonitor(pool)")
  def beforePoolInstantiation(pool: scala.concurrent.forkjoin.ForkJoinPool):Unit = {

  }
}
