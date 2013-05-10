package akka

import org.aspectj.lang.annotation._

@Aspect("perthis(poolMonitor())")
class PoolMonitorAspect {
  println("Created PoolMonitorAspect")

  @Pointcut("execution(scala.concurrent.forkjoin.ForkJoinPool.new(..)) && !within(PoolMonitorAspect)")
  protected def poolMonitor:Unit = {}

  @Before("poolMonitor() && this(pool)")
  def beforePoolInstantiation(pool: scala.concurrent.forkjoin.ForkJoinPool):Unit = {
    Tracer.collectPool(pool)
  }
}
