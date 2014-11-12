/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package akka.kamon.instrumentation

import java.lang.reflect.Method
import java.util.concurrent.ThreadPoolExecutor

import akka.actor.{ ActorSystemImpl, Cancellable }
import akka.dispatch.{ Dispatcher, Dispatchers, ExecutorServiceDelegate, MessageDispatcher }
import akka.kamon.instrumentation.DispatcherMetricsCollector.DispatcherMetricsMeasurement
import kamon.Kamon
import kamon.metric.DispatcherMetrics.DispatcherMetricRecorder
import kamon.metric.{ DispatcherMetrics, Metrics }
import org.aspectj.lang.annotation._

import scala.concurrent.forkjoin.ForkJoinPool

@Aspect
class DispatcherInstrumentation {

  @Pointcut("execution(akka.dispatch.Dispatchers.new(..)) && this(dispatchers) && cflow(execution(akka.actor.ActorSystemImpl.new(..)) && this(system))")
  def onActorSystemStartup(dispatchers: Dispatchers, system: ActorSystemImpl) = {}

  @Before("onActorSystemStartup(dispatchers, system)")
  def beforeActorSystemStartup(dispatchers: Dispatchers, system: ActorSystemImpl): Unit = {
    val currentDispatchers = dispatchers.asInstanceOf[DispatchersWithActorSystem]
    currentDispatchers.actorSystem = system
  }

  @Pointcut("execution(* akka.dispatch.Dispatchers.lookup(..)) && this(dispatchers)")
  def onDispatchersLookup(dispatchers: Dispatchers) = {}

  @AfterReturning(pointcut = "onDispatchersLookup(dispatchers)", returning = "dispatcher")
  def afterReturningLookup(dispatchers: Dispatchers, dispatcher: Dispatcher): Unit = {
    val dispatchersWithActorSystem = dispatchers.asInstanceOf[DispatchersWithActorSystem]
    val dispatcherWithMetrics = dispatcher.asInstanceOf[DispatcherMetricCollectionInfo]

    dispatcherWithMetrics.actorSystem = dispatchersWithActorSystem.actorSystem
  }

  @Pointcut("call(* akka.dispatch.ExecutorServiceFactory.createExecutorService(..))")
  def onCreateExecutorService(): Unit = {}

  @Pointcut("cflow((execution(* akka.dispatch.MessageDispatcher.registerForExecution(..)) || execution(* akka.dispatch.MessageDispatcher.executeTask(..))) && this(dispatcher))")
  def onCflowMessageDispatcher(dispatcher: Dispatcher): Unit = {}

  @Pointcut("onCreateExecutorService() && onCflowMessageDispatcher(dispatcher)")
  def onDispatcherStartup(dispatcher: Dispatcher): Unit = {}

  @After("onDispatcherStartup(dispatcher)")
  def afterDispatcherStartup(dispatcher: MessageDispatcher): Unit = {

    val dispatcherWithMetrics = dispatcher.asInstanceOf[DispatcherMetricCollectionInfo]
    val metricsExtension = Kamon(Metrics)(dispatcherWithMetrics.actorSystem)
    val metricIdentity = DispatcherMetrics(dispatcher.id)

    dispatcherWithMetrics.metricIdentity = metricIdentity
    dispatcherWithMetrics.dispatcherMetricsRecorder = metricsExtension.register(metricIdentity, DispatcherMetrics.Factory)

    if (dispatcherWithMetrics.dispatcherMetricsRecorder.isDefined) {
      dispatcherWithMetrics.dispatcherCollectorCancellable = metricsExtension.scheduleGaugeRecorder {
        dispatcherWithMetrics.dispatcherMetricsRecorder.map {
          dm ⇒
            val DispatcherMetricsMeasurement(maximumPoolSize, runningThreadCount, queueTaskCount, poolSize) =
              DispatcherMetricsCollector.collect(dispatcher)

            dm.maximumPoolSize.record(maximumPoolSize)
            dm.runningThreadCount.record(runningThreadCount)
            dm.queueTaskCount.record(queueTaskCount)
            dm.poolSize.record(poolSize)
        }
      }
    }
  }

  @Pointcut("execution(* akka.dispatch.MessageDispatcher.shutdown(..)) && this(dispatcher)")
  def onDispatcherShutdown(dispatcher: MessageDispatcher): Unit = {}

  @After("onDispatcherShutdown(dispatcher)")
  def afterDispatcherShutdown(dispatcher: MessageDispatcher): Unit = {
    val dispatcherWithMetrics = dispatcher.asInstanceOf[DispatcherMetricCollectionInfo]

    dispatcherWithMetrics.dispatcherMetricsRecorder.map {
      dispatcher ⇒
        dispatcherWithMetrics.dispatcherCollectorCancellable.cancel()
        Kamon(Metrics)(dispatcherWithMetrics.actorSystem).unregister(dispatcherWithMetrics.metricIdentity)
    }
  }
}

@Aspect
class DispatcherMetricCollectionInfoIntoDispatcherMixin {

  @DeclareMixin("akka.dispatch.MessageDispatcher")
  def mixinDispatcherMetricsToMessageDispatcher: DispatcherMetricCollectionInfo = new DispatcherMetricCollectionInfo {}

  @DeclareMixin("akka.dispatch.Dispatchers")
  def mixinDispatchersToDispatchersWithActorSystem: DispatchersWithActorSystem = new DispatchersWithActorSystem {}
}

trait DispatcherMetricCollectionInfo {
  var metricIdentity: DispatcherMetrics = _
  var dispatcherMetricsRecorder: Option[DispatcherMetricRecorder] = _
  var dispatcherCollectorCancellable: Cancellable = _
  var actorSystem: ActorSystemImpl = _
}

trait DispatchersWithActorSystem {
  var actorSystem: ActorSystemImpl = _
}

object DispatcherMetricsCollector {

  case class DispatcherMetricsMeasurement(maximumPoolSize: Long, runningThreadCount: Long, queueTaskCount: Long, poolSize: Long)

  private def collectForkJoinMetrics(pool: ForkJoinPool): DispatcherMetricsMeasurement = {
    DispatcherMetricsMeasurement(pool.getParallelism, pool.getActiveThreadCount,
      (pool.getQueuedTaskCount + pool.getQueuedSubmissionCount), pool.getPoolSize)
  }

  private def collectExecutorMetrics(pool: ThreadPoolExecutor): DispatcherMetricsMeasurement = {
    DispatcherMetricsMeasurement(pool.getMaximumPoolSize, pool.getActiveCount, pool.getQueue.size(), pool.getPoolSize)
  }

  private val executorServiceMethod: Method = {
    // executorService is protected
    val method = classOf[Dispatcher].getDeclaredMethod("executorService")
    method.setAccessible(true)
    method
  }

  def collect(dispatcher: MessageDispatcher): DispatcherMetricsMeasurement = {
    dispatcher match {
      case x: Dispatcher ⇒ {
        val executor = executorServiceMethod.invoke(x) match {
          case delegate: ExecutorServiceDelegate ⇒ delegate.executor
          case other                             ⇒ other
        }

        executor match {
          case fjp: ForkJoinPool       ⇒ collectForkJoinMetrics(fjp)
          case tpe: ThreadPoolExecutor ⇒ collectExecutorMetrics(tpe)
          case anything                ⇒ DispatcherMetricsMeasurement(0L, 0L, 0L, 0L)
        }
      }
      case _ ⇒ new DispatcherMetricsMeasurement(0L, 0L, 0L, 0L)
    }
  }
}
