package akka

import org.aspectj.lang.annotation.{Pointcut, Before, Aspect}
import java.util.concurrent.{TimeUnit, Executors}

@Aspect
class PoolMonitorAspect {

  @Pointcut("execution(scala.concurrent.forkjoin.ForkJoinPool.new(..))")
  protected def poolMonitor:Unit = {}

  @Before("poolMonitor() && this(pool)")
  def beforePoolInstantiation(pool: scala.concurrent.forkjoin.ForkJoinPool) {

       val scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(new Runnable {
          def run() {
                println("PoolName : " + pool.getClass.getSimpleName)
                println("ThreadCount :" + pool.getActiveThreadCount)
                println("Parallelism :" + pool.getParallelism)
                println("PoolSize :" + pool.getPoolSize())
                println("Submission :" + pool.getQueuedSubmissionCount())
                println("Steals :" + pool.getStealCount())
                println("All :" + pool.toString)
          }
        }, 4, 4, TimeUnit.SECONDS)
      }
}
