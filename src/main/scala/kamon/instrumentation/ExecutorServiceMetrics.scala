package kamon.instrumentation

import org.aspectj.lang.annotation._
import java.util.concurrent._
import org.aspectj.lang.ProceedingJoinPoint
import java.util
import kamon.metric.{MetricDirectory, ExecutorServiceMetricCollector}
import akka.dispatch.{MonitorableThreadFactory, ExecutorServiceFactory}


case class NamedExecutorServiceFactoryDelegate(actorSystemName: String, dispatcherName: String, delegate: ExecutorServiceFactory) extends ExecutorServiceFactory {
  def createExecutorService: ExecutorService = delegate.createExecutorService
}

@Aspect
class ExecutorServiceFactoryProviderInstrumentation {

  @Pointcut("execution(* akka.dispatch.ExecutorServiceFactoryProvider+.createExecutorServiceFactory(..)) && args(id, threadFactory)")
  def factoryMethodCall(id: String, threadFactory: ThreadFactory) = {}

  @Around("factoryMethodCall(id, threadFactory)")
  def enrichFactoryCreationWithNames(pjp: ProceedingJoinPoint, id: String, threadFactory: ThreadFactory): ExecutorServiceFactory = {
    val delegate = pjp.proceed().asInstanceOf[ExecutorServiceFactory] // Safe Cast

    val actorSystemName = threadFactory match {
      case m: MonitorableThreadFactory => m.name
      case _ => "Unknown"   // Find an alternative way to find the actor system name in case we start seeing "Unknown" as the AS name.
    }

    new NamedExecutorServiceFactoryDelegate(actorSystemName, id, delegate)
  }

}


@Aspect
class NamedExecutorServiceFactoryDelegateInstrumentation {

  @Pointcut("execution(* akka.dispatch.NamedExecutorServiceFactoryDelegate.createExecutorService()) && this(namedFactory)")
  def factoryMethodCall(namedFactory: NamedExecutorServiceFactoryDelegate) = {}

  @Around("factoryMethodCall(namedFactory)")
  def enrichExecutorServiceWithMetricNameRoot(pjp: ProceedingJoinPoint, namedFactory: NamedExecutorServiceFactoryDelegate): ExecutorService = {
    val delegate = pjp.proceed().asInstanceOf[ExecutorService]
    val executorFullName = MetricDirectory.nameForDispatcher(namedFactory.actorSystemName, namedFactory.dispatcherName)

    ExecutorServiceMetricCollector.register(executorFullName, delegate)

    new NamedExecutorServiceDelegate(executorFullName, delegate)
  }
}

case class NamedExecutorServiceDelegate(fullName: String, delegate: ExecutorService) extends ExecutorService {
  def shutdown() = {
    ExecutorServiceMetricCollector.deregister(fullName)
    delegate.shutdown()
  }
  def shutdownNow(): util.List[Runnable] = delegate.shutdownNow()
  def isShutdown: Boolean = delegate.isShutdown
  def isTerminated: Boolean = delegate.isTerminated
  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = delegate.awaitTermination(timeout, unit)
  def submit[T](task: Callable[T]): Future[T] = delegate.submit(task)
  def submit[T](task: Runnable, result: T): Future[T] = delegate.submit(task, result)
  def submit(task: Runnable): Future[_] = delegate.submit(task)
  def invokeAll[T](tasks: util.Collection[_ <: Callable[T]]): util.List[Future[T]] = delegate.invokeAll(tasks)
  def invokeAll[T](tasks: util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): util.List[Future[T]] = delegate.invokeAll(tasks, timeout, unit)
  def invokeAny[T](tasks: util.Collection[_ <: Callable[T]]): T = delegate.invokeAny(tasks)
  def invokeAny[T](tasks: util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): T = delegate.invokeAny(tasks, timeout, unit)
  def execute(command: Runnable) = delegate.execute(command)
}