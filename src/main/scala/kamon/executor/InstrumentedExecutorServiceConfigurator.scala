package kamon.executor

import akka.dispatch.{ExecutorServiceFactory, ForkJoinExecutorConfigurator, DispatcherPrerequisites}
import com.typesafe.config.Config
import scala.concurrent.forkjoin.ForkJoinPool
import java.util.concurrent.{Future, TimeUnit, Callable, ExecutorService}
import akka.dispatch.ForkJoinExecutorConfigurator.AkkaForkJoinPool
import java.util

class InstrumentedExecutorServiceConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
  extends ForkJoinExecutorConfigurator(config, prerequisites) {

  println("Created the instrumented executor")


  class InstrumentedExecutorServiceFactory(threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory, parallelism: Int)
    extends ForkJoinExecutorServiceFactory(threadFactory, parallelism) {


    override def createExecutorService: ExecutorService = {
      super.createExecutorService match {
        case fjp: AkkaForkJoinPool => new WrappedPool(fjp)
        case other => other
      }
    }
  }

}

case class ForkJoinPoolMetrics(activeThreads: Int, queueSize: Long)

class WrappedPool(val fjp: AkkaForkJoinPool) extends ExecutorService {


  def metrics = ForkJoinPoolMetrics(fjp.getActiveThreadCount(), fjp.getQueuedTaskCount)

  def shutdown = fjp.shutdown()

  def shutdownNow(): util.List[Runnable] = fjp.shutdownNow()

  def isShutdown: Boolean = fjp.isShutdown

  def isTerminated: Boolean = fjp.isTerminated

  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = fjp.awaitTermination(timeout, unit)

  def submit[T](task: Callable[T]): Future[T] = fjp.submit(task)

  def submit[T](task: Runnable, result: T): Future[T] = fjp.submit(task, result)

  def submit(task: Runnable): Future[_] = fjp.submit(task)

  def invokeAll[T](tasks: util.Collection[_ <: Callable[T]]): util.List[Future[T]] = fjp.invokeAll(tasks)

  def invokeAll[T](tasks: util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): util.List[Future[T]] = fjp.invokeAll(tasks, timeout, unit)

  def invokeAny[T](tasks: util.Collection[_ <: Callable[T]]): T = fjp.invokeAny(tasks)

  def invokeAny[T](tasks: util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): T = fjp.invokeAny(tasks)

  def execute(command: Runnable) = fjp.execute(command)
}

