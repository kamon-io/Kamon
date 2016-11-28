/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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
import java.util.concurrent.{ ExecutorService, ThreadPoolExecutor }

import akka.actor.{ ActorContext, Props, ActorSystem, ActorSystemImpl }
import akka.dispatch._
import akka.kamon.instrumentation.LookupDataAware.LookupData
import kamon.Kamon
import kamon.util.executors.{ ForkJoinPoolMetrics, ThreadPoolExecutorMetrics }

import kamon.metric.{ MetricsModule, Entity }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

import scala.concurrent.forkjoin.ForkJoinPool

@Aspect
class DispatcherInstrumentation {

  @Pointcut("execution(* akka.actor.ActorSystemImpl.start(..)) && this(system)")
  def actorSystemInitialization(system: ActorSystemImpl): Unit = {}

  @Before("actorSystemInitialization(system)")
  def afterActorSystemInitialization(system: ActorSystemImpl): Unit = {
    system.dispatchers.asInstanceOf[ActorSystemAware].actorSystem = system

    // The default dispatcher for the actor system is looked up in the ActorSystemImpl's initialization code and we
    // can't get the Metrics extension there since the ActorSystem is not yet fully constructed. To workaround that
    // we are manually selecting and registering the default dispatcher with the Metrics extension. All other dispatchers
    // will by registered by the instrumentation bellow.

    // Yes, reflection sucks, but this piece of code is only executed once on ActorSystem's startup.
    val defaultDispatcher = system.dispatcher
    val defaultDispatcherExecutor = extractExecutor(defaultDispatcher.asInstanceOf[MessageDispatcher])
    registerDispatcher(Dispatchers.DefaultDispatcherId, defaultDispatcherExecutor, system)
  }

  private def extractExecutor(dispatcher: MessageDispatcher): ExecutorService = {
    val executorServiceMethod: Method = {
      // executorService is protected
      val method = classOf[Dispatcher].getDeclaredMethod("executorService")
      method.setAccessible(true)
      method
    }

    dispatcher match {
      case x: Dispatcher ⇒
        val executor = executorServiceMethod.invoke(x) match {
          case delegate: ExecutorServiceDelegate ⇒ delegate.executor
          case other                             ⇒ other
        }
        executor.asInstanceOf[ExecutorService]
    }
  }

  private def registerDispatcher(dispatcherName: String, executorService: ExecutorService, system: ActorSystem): Unit =
    executorService match {
      case fjp: ForkJoinPool ⇒
        val dispatcherEntity = Entity(system.name + "/" + dispatcherName, AkkaDispatcherMetrics.Category, tags = Map("dispatcher-type" -> "fork-join-pool"))

        if (Kamon.metrics.shouldTrack(dispatcherEntity))
          Kamon.metrics.entity(ForkJoinPoolMetrics.factory(fjp, AkkaDispatcherMetrics.Category), dispatcherEntity)

      case tpe: ThreadPoolExecutor ⇒
        val dispatcherEntity = Entity(system.name + "/" + dispatcherName, AkkaDispatcherMetrics.Category, tags = Map("dispatcher-type" -> "thread-pool-executor"))

        if (Kamon.metrics.shouldTrack(dispatcherEntity))
          Kamon.metrics.entity(ThreadPoolExecutorMetrics.factory(tpe, AkkaDispatcherMetrics.Category), dispatcherEntity)

      case others ⇒ // Currently not interested in other kinds of dispatchers.
    }

  @Pointcut("execution(* akka.dispatch.Dispatchers.lookup(..)) && this(dispatchers) && args(dispatcherName)")
  def dispatchersLookup(dispatchers: ActorSystemAware, dispatcherName: String) = {}

  @Around("dispatchersLookup(dispatchers, dispatcherName)")
  def aroundDispatchersLookup(pjp: ProceedingJoinPoint, dispatchers: ActorSystemAware, dispatcherName: String): Any =
    LookupDataAware.withLookupData(LookupData(dispatcherName, dispatchers.actorSystem)) {
      pjp.proceed()
    }

  @Pointcut("initialization(akka.dispatch.ExecutorServiceFactory.new(..)) && target(factory)")
  def executorServiceFactoryInitialization(factory: LookupDataAware): Unit = {}

  @After("executorServiceFactoryInitialization(factory)")
  def afterExecutorServiceFactoryInitialization(factory: LookupDataAware): Unit =
    factory.lookupData = LookupDataAware.currentLookupData

  @Pointcut("execution(* akka.dispatch.ExecutorServiceFactory+.createExecutorService()) && this(factory) && !cflow(execution(* akka.dispatch.Dispatcher.shutdown()))")
  def createExecutorService(factory: LookupDataAware): Unit = {}

  @AfterReturning(pointcut = "createExecutorService(factory)", returning = "executorService")
  def afterCreateExecutorService(factory: LookupDataAware, executorService: ExecutorService): Unit = {
    val lookupData = factory.lookupData

    // lookupData.actorSystem will be null only during the first lookup of the default dispatcher during the
    // ActorSystemImpl's initialization.
    if (lookupData.actorSystem != null)
      registerDispatcher(lookupData.dispatcherName, executorService, lookupData.actorSystem)
  }

  @Pointcut("initialization(akka.dispatch.Dispatcher.LazyExecutorServiceDelegate.new(..)) && this(lazyExecutor)")
  def lazyExecutorInitialization(lazyExecutor: LookupDataAware): Unit = {}

  @After("lazyExecutorInitialization(lazyExecutor)")
  def afterLazyExecutorInitialization(lazyExecutor: LookupDataAware): Unit =
    lazyExecutor.lookupData = LookupDataAware.currentLookupData

  @Pointcut("execution(* akka.dispatch.Dispatcher.LazyExecutorServiceDelegate.copy()) && this(lazyExecutor)")
  def lazyExecutorCopy(lazyExecutor: LookupDataAware): Unit = {}

  @Around("lazyExecutorCopy(lazyExecutor)")
  def aroundLazyExecutorCopy(pjp: ProceedingJoinPoint, lazyExecutor: LookupDataAware): Any =
    LookupDataAware.withLookupData(lazyExecutor.lookupData) {
      pjp.proceed()
    }

  @Pointcut("execution(* akka.dispatch.Dispatcher.LazyExecutorServiceDelegate.shutdown()) && this(lazyExecutor)")
  def lazyExecutorShutdown(lazyExecutor: LookupDataAware): Unit = {}

  @After("lazyExecutorShutdown(lazyExecutor)")
  def afterLazyExecutorShutdown(lazyExecutor: LookupDataAware): Unit = {
    import lazyExecutor.lookupData

    if (lookupData.actorSystem != null)
      lazyExecutor.asInstanceOf[ExecutorServiceDelegate].executor match {
        case fjp: ForkJoinPool ⇒
          lookupData.metrics.removeEntity(Entity(lookupData.actorSystem.name + "/" + lookupData.dispatcherName,
            AkkaDispatcherMetrics.Category, tags = Map("dispatcher-type" -> "fork-join-pool")))

        case tpe: ThreadPoolExecutor ⇒
          lookupData.metrics.removeEntity(Entity(lookupData.actorSystem.name + "/" + lookupData.dispatcherName,
            AkkaDispatcherMetrics.Category, tags = Map("dispatcher-type" -> "thread-pool-executor")))

        case other ⇒ // nothing to remove.
      }
  }

  @Pointcut("execution(* akka.routing.BalancingPool.newRoutee(..)) && args(props, context)")
  def createNewRouteeOnBalancingPool(props: Props, context: ActorContext): Unit = {}

  @Around("createNewRouteeOnBalancingPool(props, context)")
  def aroundCreateNewRouteeOnBalancingPool(pjp: ProceedingJoinPoint, props: Props, context: ActorContext): Any = {
    val deployPath = context.self.path.elements.drop(1).mkString("/", "/", "")
    val dispatcherId = s"BalancingPool-$deployPath"

    LookupDataAware.withLookupData(LookupData(dispatcherId, context.system)) {
      pjp.proceed()
    }
  }
}

@Aspect
class DispatcherMetricCollectionInfoIntoDispatcherMixin {

  @DeclareMixin("akka.dispatch.Dispatchers")
  def mixinActorSystemAwareToDispatchers: ActorSystemAware = ActorSystemAware()

  @DeclareMixin("akka.dispatch.Dispatcher.LazyExecutorServiceDelegate")
  def mixinLookupDataAwareToExecutors: LookupDataAware = LookupDataAware()

  @DeclareMixin("akka.dispatch.ExecutorServiceFactory+")
  def mixinActorSystemAwareToDispatcher: LookupDataAware = LookupDataAware()
}

trait ActorSystemAware {
  @volatile var actorSystem: ActorSystem = _
}

object ActorSystemAware {
  def apply(): ActorSystemAware = new ActorSystemAware {}
}

trait LookupDataAware {
  @volatile var lookupData: LookupData = _
}

object LookupDataAware {
  case class LookupData(dispatcherName: String, actorSystem: ActorSystem, metrics: MetricsModule = Kamon.metrics)

  private val _currentDispatcherLookupData = new ThreadLocal[LookupData]

  def apply() = new LookupDataAware {}

  def currentLookupData: LookupData = _currentDispatcherLookupData.get()

  def withLookupData[T](lookupData: LookupData)(thunk: ⇒ T): T = {
    _currentDispatcherLookupData.set(lookupData)
    val result = thunk
    _currentDispatcherLookupData.remove()

    result
  }
}

object AkkaDispatcherMetrics {
  val Category = "akka-dispatcher"
}
