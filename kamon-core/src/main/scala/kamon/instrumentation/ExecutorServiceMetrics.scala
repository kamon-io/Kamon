package kamon.instrumentation

import org.aspectj.lang.annotation._
import java.util.concurrent._
import org.aspectj.lang.ProceedingJoinPoint
import java.util
import kamon.metric.{DispatcherMetricCollector, Histogram, MetricDirectory, ExecutorServiceMetricCollector}
import akka.dispatch.{MonitorableThreadFactory, ExecutorServiceFactory}
import com.typesafe.config.Config
import kamon.Kamon
import scala.concurrent.forkjoin.ForkJoinPool
import akka.dispatch.ForkJoinExecutorConfigurator.AkkaForkJoinPool


@Aspect
class ActorSystemInstrumentation {

  @Pointcut("execution(akka.actor.ActorSystemImpl.new(..)) && args(name, applicationConfig, classLoader)")
  def actorSystemInstantiation(name: String, applicationConfig: Config, classLoader: ClassLoader) = {}

  @After("actorSystemInstantiation(name, applicationConfig, classLoader)")
  def registerActorSystem(name: String, applicationConfig: Config, classLoader: ClassLoader): Unit = {

    Kamon.Metric.registerActorSystem(name)
  }
}

@Aspect("perthis(forkJoinPoolInstantiation(int, scala.concurrent.forkjoin.ForkJoinPool.ForkJoinWorkerThreadFactory, java.lang.Thread.UncaughtExceptionHandler))")
class ForkJoinPoolInstrumentation {
  var activeThreadsHistogram: Histogram = _
  var poolSizeHistogram: Histogram = _

  @Pointcut("execution(akka.dispatch.ForkJoinExecutorConfigurator.AkkaForkJoinPool.new(..)) && args(parallelism, threadFactory, exceptionHandler)")
  def forkJoinPoolInstantiation(parallelism: Int, threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory, exceptionHandler: Thread.UncaughtExceptionHandler) = {}

  @After("forkJoinPoolInstantiation(parallelism, threadFactory, exceptionHandler)")
  def initializeMetrics(parallelism: Int, threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory, exceptionHandler: Thread.UncaughtExceptionHandler): Unit = {
    val (actorSystemName, dispatcherName) = threadFactory match {
      case mtf: MonitorableThreadFactory => splitName(mtf.name, Kamon.Metric.actorSystemNames)
      case _ => ("Unknown", "Unknown")
    }

    val metrics = Kamon.Metric.actorSystem(actorSystemName).get.registerDispatcher(dispatcherName)
    for(m <- metrics) {
      activeThreadsHistogram = m.activeThreadCount
      poolSizeHistogram = m.poolSize
      println(s"Registered $dispatcherName for actor system $actorSystemName")
    }
  }

  def splitName(threadFactoryName: String, knownActorSystems: List[String]): (String, String) = {
    knownActorSystems.find(threadFactoryName.startsWith(_)).map(asName => (asName, threadFactoryName.substring(asName.length+1))).getOrElse(("Unkown", "Unkown"))
  }




  @Pointcut("execution(* scala.concurrent.forkjoin.ForkJoinPool.scan(..)) && this(fjp)")
  def forkJoinScan(fjp: AkkaForkJoinPool): Unit = {}

  @After("forkJoinScan(fjp)")
  def updateMetrics(fjp: AkkaForkJoinPool): Unit = {
    activeThreadsHistogram.update(fjp.getActiveThreadCount)
    poolSizeHistogram.update(fjp.getPoolSize)
  }



}






























/**
 *  ExecutorService monitoring base:
 */
trait ExecutorServiceCollector {
  def updateActiveThreadCount(diff: Int): Unit
  def updateTotalThreadCount(diff: Int): Unit
  def updateQueueSize(diff: Int): Unit
}

trait WatchedExecutorService {
  def collector: ExecutorServiceCollector
}














trait ExecutorServiceMonitoring {
  def dispatcherMetrics: DispatcherMetricCollector
}

class ExecutorServiceMonitoringImpl extends ExecutorServiceMonitoring {
  @volatile var dispatcherMetrics: DispatcherMetricCollector = _
}
















case class NamedExecutorServiceFactoryDelegate(actorSystemName: String, dispatcherName: String, delegate: ExecutorServiceFactory) extends ExecutorServiceFactory {
  def createExecutorService: ExecutorService = delegate.createExecutorService
}

@Aspect
class ExecutorServiceFactoryProviderInstrumentation {

  @Pointcut("execution(* akka.dispatch.ExecutorServiceFactoryProvider+.createExecutorServiceFactory(..)) && args(dispatcherName, threadFactory) && if()")
  def factoryMethodCall(dispatcherName: String, threadFactory: ThreadFactory): Boolean = {
    true
  }

  @Around("factoryMethodCall(dispatcherName, threadFactory)")
  def enrichFactoryCreationWithNames(pjp: ProceedingJoinPoint, dispatcherName: String, threadFactory: ThreadFactory): ExecutorServiceFactory = {
    val delegate = pjp.proceed().asInstanceOf[ExecutorServiceFactory] // Safe Cast

    val actorSystemName = threadFactory match {
      case m: MonitorableThreadFactory => m.name
      case _ => "Unknown"   // Find an alternative way to find the actor system name in case we start seeing "Unknown" as the AS name.
    }

    new NamedExecutorServiceFactoryDelegate(actorSystemName, dispatcherName, delegate)
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



































