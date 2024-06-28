/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.executor

import java.time.Duration
import java.util
import java.util.concurrent.{
  Callable,
  ExecutorService,
  Future,
  ScheduledExecutorService,
  ScheduledFuture,
  ScheduledThreadPoolExecutor,
  ThreadPoolExecutor,
  TimeUnit,
  ForkJoinPool => JavaForkJoinPool
}
import com.typesafe.config.Config
import kamon.Kamon
import kamon.jsr166.LongAdder
import kamon.metric.Counter
import kamon.module.ScheduledAction
import kamon.tag.TagSet
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.util.Try

object ExecutorInstrumentation {

  private val _logger = LoggerFactory.getLogger("kamon.instrumentation.executors.ExecutorsInstrumentation")
  @volatile private var _sampleInterval = readSampleInterval(Kamon.config())
  Kamon.onReconfigure(newConfig => _sampleInterval = readSampleInterval(newConfig))

  /**
    * Creates a new instrumented ExecutorService that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala). All metrics related to the
    * instrumented service will have the following tags:
    *
    *   * name: set to the provided name parameter.
    *   * type: set to "ThreadPoolExecutor" executors or "ForkJoinPool".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrument(executor: ExecutorService, name: String): ExecutorService =
    instrument(executor, name, TagSet.Empty, DefaultSettings)

  /**
    * Creates a new instrumented ExecutionContext that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala). All metrics related to the
    * instrumented service will have the following tags:
    *
    *   * name: set to the provided name parameter.
    *   * type: set to "ThreadPoolExecutor" executors or "ForkJoinPool".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrumentExecutionContext(executionContext: ExecutionContext, name: String): InstrumentedExecutionContext =
    instrumentExecutionContext(executionContext, name, TagSet.Empty, name, DefaultSettings)

  /**
    * Creates a new instrumented ScheduledExecutorService that wraps the provided one. The instrumented executor will
    * track metrics for a ScheduledThreadPoolExecutor, but will not perform any context propagation nor track the time
    * in queue metric for submitted tasks.
    *
    * All metrics related to the instrumented service will have the following tags:
    *   * name: set to the provided name parameter.
    *   * type: set to "ScheduledThreadPoolExecutor".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrumentScheduledExecutor(executor: ScheduledExecutorService, name: String): ScheduledExecutorService =
    instrumentScheduledExecutor(executor, name, TagSet.Empty, name)

  /**
    * Creates a new instrumented ExecutorService that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala) and optionally, track the
    * time spent by each task on the wrapped executor's queue.
    *
    * All metrics related to the instrumented service will have the following tags:
    *
    *   * name: set to the provided name parameter.
    *   * type: set to "ThreadPoolExecutor" executors or "ForkJoinPool".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrument(executor: ExecutorService, name: String, settings: Settings): ExecutorService =
    instrument(executor, name, TagSet.Empty, settings)

  /**
    * Creates a new instrumented ExecutionContext that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala) and optionally, track the
    * time spent by each task on the wrapped executor's queue.
    *
    * All metrics related to the instrumented service will have the following tags:
    *
    *   * name: set to the provided name parameter.
    *   * type: set to "ThreadPoolExecutor" executors or "ForkJoinPool".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrumentExecutionContext(
    executionContext: ExecutionContext,
    name: String,
    settings: Settings
  ): InstrumentedExecutionContext =
    instrumentExecutionContext(executionContext, name, TagSet.Empty, name, settings)

  /**
    * Creates a new instrumented ExecutorService that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala). All metrics related to the
    * instrumented service will have the following tags:
    *
    *   * all of the provided extraTags (take into account that any "name" or "type" tags will be overwritten.
    *   * name: set to the provided name parameter.
    *   * type: set to "ThreadPoolExecutor" executors or "ForkJoinPool".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrument(executor: ExecutorService, name: String, extraTags: TagSet): ExecutorService =
    instrument(executor, name, extraTags, DefaultSettings)

  /**
    * Creates a new instrumented ExecutorService that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala). All metrics related to the
    * instrumented service will have the following tags:
    *
    *   * all of the provided extraTags (take into account that any "name" or "type" tags will be overwritten.
    *   * name: set to the provided name parameter.
    *   * type: set to "ThreadPoolExecutor" executors or "ForkJoinPool".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrumentExecutionContext(
    executionContext: ExecutionContext,
    name: String,
    extraTags: TagSet
  ): InstrumentedExecutionContext =
    instrumentExecutionContext(executionContext, name, extraTags, name, DefaultSettings)

  /**
    * Creates a new instrumented ExecutorService that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala) and optionally, track the
    * time spent by each task on the wrapped executor's queue.
    *
    * All metrics related to the instrumented service will have the following tags:
    *   * all of the provided extraTags (take into account that any "name" or "type" tags will be overwritten.
    *   * name: set to the provided name parameter.
    *   * type: set to "ThreadPoolExecutor" executors or "ForkJoinPool".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrument(executor: ExecutorService, name: String, extraTags: TagSet, settings: Settings): ExecutorService =
    instrument(executor, name, extraTags, name, settings)

  /**
    * Creates a new instrumented ExecutorService that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala) and optionally, track the
    * time spent by each task on the wrapped executor's queue.
    *
    * All metrics related to the instrumented service will have the following tags:
    *   * all of the provided extraTags (take into account that any "name" or "type" tags will be overwritten.
    *   * name: set to the provided name parameter.
    *   * type: set to "ThreadPoolExecutor" executors or "ForkJoinPool".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrument(
    executor: ExecutorService,
    name: String,
    extraTags: TagSet,
    scheduledActionName: String,
    settings: Settings
  ): ExecutorService = {
    executor match {
      case tpe: ThreadPoolExecutor => new InstrumentedThreadPool(tpe, name, extraTags, scheduledActionName, settings)
      case jfjp: JavaForkJoinPool => new InstrumentedForkJoinPool(
          jfjp,
          ForkJoinPoolTelemetryReader.forJava(jfjp),
          name,
          extraTags,
          scheduledActionName,
          settings
        )
      case sfjp: ScalaForkJoinPool => new InstrumentedForkJoinPool(
          sfjp,
          ForkJoinPoolTelemetryReader.forScala(sfjp),
          name,
          extraTags,
          scheduledActionName,
          settings
        )
      case anyOther =>
        _logger.warn("Cannot instrument unknown executor [{}]", anyOther)
        executor
    }
  }

  /**
    * Creates a new instrumented ScheduledExecutorService that wraps the provided one. The instrumented executor will
    * track metrics for a ScheduledThreadPoolExecutor, but will not perform any context propagation nor track the time
    * in queue metric for submitted tasks.
    *
    * All metrics related to the instrumented service will have the following tags:
    *   * all of the provided extraTags (take into account that any "name" or "type" tags will be overwritten.
    *   * name: set to the provided name parameter.
    *   * type: set to "ScheduledThreadPoolExecutor".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrumentScheduledExecutor(
    executor: ScheduledExecutorService,
    name: String,
    extraTags: TagSet
  ): ScheduledExecutorService =
    instrumentScheduledExecutor(executor, name, extraTags, name)

  /**
    * Creates a new instrumented ScheduledExecutorService that wraps the provided one. The instrumented executor will
    * track metrics for a ScheduledThreadPoolExecutor, but will not perform any context propagation nor track the time
    * in queue metric for submitted tasks.
    *
    * All metrics related to the instrumented service will have the following tags:
    *   * all of the provided extraTags (take into account that any "name" or "type" tags will be overwritten.
    *   * name: set to the provided name parameter.
    *   * type: set to "ScheduledThreadPoolExecutor".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrumentScheduledExecutor(
    executor: ScheduledExecutorService,
    name: String,
    extraTags: TagSet,
    scheduledActionName: String
  ): ScheduledExecutorService = {

    executor match {

      case stpe: ScheduledThreadPoolExecutor =>
        new InstrumentedScheduledThreadPoolExecutor(
          stpe,
          name,
          extraTags.withTag("scheduled", true),
          scheduledActionName
        )

      case anyOther =>
        _logger.warn("Cannot instrument unknown executor [{}]", anyOther)
        executor
    }
  }

  /**
    * Creates a new instrumented ExecutionContext that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala) and optionally, track the
    * time spent by each task on the wrapped executor's queue.
    *
    * All metrics related to the instrumented service will have the following tags:
    *   * all of the provided extraTags (take into account that any "name" or "type" tags will be overwritten.
    *   * name: set to the provided name parameter.
    *   * type: set to "ThreadPoolExecutor" executors or "ForkJoinPool".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrumentExecutionContext(
    executionContext: ExecutionContext,
    name: String,
    extraTags: TagSet,
    scheduledActionName: String,
    settings: Settings
  ): InstrumentedExecutionContext = {

    val underlyingExecutor = unwrapExecutionContext(executionContext)
      .map(executor => instrument(executor, name, extraTags, scheduledActionName, settings))

    new InstrumentedExecutionContext(executionContext, underlyingExecutor)
  }

  /**
    * Creates a new instrumented ExecutorService that wraps the provided one, assuming that the wrapped executor is a
    * form of ForkJoinPool implementation. The instrumented executor will track all the common pool metrics and
    * optionally, track the time spent by each task on the wrapped executor's queue.
    *
    * All metrics related to the instrumented service will have the following tags:
    *   * all of the provided extraTags (take into account that any "name" or "type" tags will be overwritten.
    *   * name: set to the provided name parameter.
    *   * type: set to "ThreadPoolExecutor" executors or "ForkJoinPool".
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrument(
    executor: ExecutorService,
    telemetryReader: ForkJoinPoolTelemetryReader,
    name: String,
    extraTags: TagSet,
    scheduledActionName: String,
    settings: Settings
  ): ExecutorService = {
    new InstrumentedForkJoinPool(executor, telemetryReader, name, extraTags, scheduledActionName, settings)
  }

  /**
    * We do not perform context propagation on submit by default because the automatic instrumentation provided by
    * this module should ensure that all interesting Runnable/Callable implementations capture a Context instance.
    * Furthermore, in many situations the current Context when a Runnable/Callable is created is different from the
    * current context when it is submitted for execution and in most situations it is safer to assume that all
    * Runnable/Callable should capture the current Context at the instant when they are created, not when submitted.
    */
  val DefaultSettings = new Settings(shouldTrackTimeInQueue = true, shouldPropagateContextOnSubmit = false)

  /**
    * Settings that do not enable any extra features on the instrumented executor service.
    */
  val NoExtraSettings = new Settings(shouldTrackTimeInQueue = false, shouldPropagateContextOnSubmit = false)

  class Settings(val shouldTrackTimeInQueue: Boolean, val shouldPropagateContextOnSubmit: Boolean) {

    def trackTimeInQueue(): Settings =
      new Settings(true, shouldPropagateContextOnSubmit)

    def doNotTrackTimeInQueue(): Settings =
      new Settings(false, shouldPropagateContextOnSubmit)

    def propagateContextOnSubmit(): Settings =
      new Settings(shouldTrackTimeInQueue, true)

    def doNotPropagateContextOnSubmit(): Settings =
      new Settings(shouldTrackTimeInQueue, false)
  }

  private val _executionContextExecutorField = {
    val field = Class.forName("scala.concurrent.impl.ExecutionContextImpl").getDeclaredField("executor")
    field.setAccessible(true)
    field
  }

  private def unwrapExecutionContext(executionContext: ExecutionContext): Option[ExecutorService] =
    try {

      // This function can only unwrap ExecutionContext instances created via ExecutionContext.fromExecutor
      // or ExecutionContext.fromExecutorService.
      Some(_executionContextExecutorField.get(executionContext).asInstanceOf[ExecutorService])
    } catch {
      case _: Throwable =>
        _logger.warn("Cannot unwrap unsupported ExecutionContext [{}]", executionContext)
        None
    }

  /**
    * Abstracts the means of reading some telemetry information from concrete executor implementations. This allows us
    * to track the same metrics even when coming from slightly different implementations. The three cases we have seen
    * so far where this is useful are when instrumenting: the ForkJoinPool included in the JDK (since Java 8), the one
    * included in Scala 2.11 Library and the one shipped with Akka.
    */
  trait ForkJoinPoolTelemetryReader {
    def activeThreads: Int
    def poolSize: Int
    def queuedTasks: Int
    def parallelism: Int
  }

  object ForkJoinPoolTelemetryReader {

    def forJava(pool: JavaForkJoinPool): ForkJoinPoolTelemetryReader = new ForkJoinPoolTelemetryReader {
      override def activeThreads: Int = pool.getActiveThreadCount
      override def poolSize: Int = pool.getPoolSize
      override def queuedTasks: Int = pool.getQueuedSubmissionCount
      override def parallelism: Int = pool.getParallelism
    }

    def forScala(pool: ScalaForkJoinPool): ForkJoinPoolTelemetryReader = new ForkJoinPoolTelemetryReader {
      override def activeThreads: Int = pool.getActiveThreadCount
      override def poolSize: Int = pool.getPoolSize
      override def queuedTasks: Int = pool.getQueuedSubmissionCount
      override def parallelism: Int = pool.getParallelism
    }
  }

  private def readSampleInterval(config: Config): Duration =
    Try(Kamon.config().getDuration("kamon.instrumentation.executor.sample-interval"))
      .getOrElse(Duration.ofSeconds(10))

  private trait CallableWrapper {
    def wrap[T](callable: Callable[T]): Callable[T]
  }

  /**
    * Executor service wrapper for ThreadPool executors that keeps track of submitted and completed tasks and
    * optionally tracks the time tasks spend waiting on the underlying executor service's queue.
    *
    * The instruments used to track the pool's behavior are removed once the pool is shut down.
    */
  class InstrumentedThreadPool(
    wrapped: ThreadPoolExecutor,
    name: String,
    extraTags: TagSet,
    scheduledActionName: String,
    settings: Settings
  ) extends ExecutorService {

    private val _runnableWrapper = buildRunnableWrapper()
    private val _callableWrapper = buildCallableWrapper()
    private val _instruments = new ExecutorMetrics.ThreadPoolInstruments(name, extraTags, executorType)
    private val _timeInQueueTimer = _instruments.timeInQueue
    private val _collectorRegistration = Kamon.addScheduledAction(
      scheduledActionName,
      Some(s"Updates health metrics for the ${name} thread pool every ${_sampleInterval.getSeconds} seconds"),
      new ScheduledAction {
        val submittedTasksSource = Counter.delta(() => wrapped.getTaskCount)
        val completedTaskCountSource = Counter.delta(() => wrapped.getCompletedTaskCount)

        override def run(): Unit = {
          _instruments.poolMin.update(wrapped.getCorePoolSize)
          _instruments.poolMax.update(wrapped.getMaximumPoolSize)
          _instruments.totalThreads.record(wrapped.getPoolSize)
          _instruments.activeThreads.record(wrapped.getActiveCount)
          _instruments.queuedTasks.record(wrapped.getQueue.size())
          submittedTasksSource.accept(_instruments.submittedTasks)
          completedTaskCountSource.accept(_instruments.completedTasks)
        }

        override def stop(): Unit = {}
        override def reconfigure(newConfig: Config): Unit = {}
      },
      _sampleInterval
    )

    protected def executorType: String =
      "ThreadPoolExecutor"

    override def execute(command: Runnable): Unit =
      wrapped.execute(_runnableWrapper(command))

    override def submit(task: Runnable): Future[_] =
      wrapped.submit(_runnableWrapper(task))

    override def submit[T](task: Runnable, result: T): Future[T] =
      wrapped.submit(_runnableWrapper(task), result)

    override def submit[T](task: Callable[T]): Future[T] =
      wrapped.submit(_callableWrapper.wrap(task))

    override def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]]): java.util.List[Future[T]] =
      wrapped.invokeAll(wrapTasks(tasks).asInstanceOf[java.util.Collection[Callable[T]]])

    override def invokeAll[T](
      tasks: java.util.Collection[_ <: Callable[T]],
      timeout: Long,
      unit: TimeUnit
    ): java.util.List[Future[T]] =
      wrapped.invokeAll(wrapTasks(tasks).asInstanceOf[java.util.Collection[Callable[T]]], timeout, unit)

    override def isTerminated: Boolean =
      wrapped.isTerminated

    override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
      wrapped.awaitTermination(timeout, unit)

    override def shutdownNow(): java.util.List[Runnable] = {
      _collectorRegistration.cancel()
      _instruments.remove()
      wrapped.shutdownNow()
    }

    override def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]]): T =
      wrapped.invokeAny(tasks)

    override def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): T =
      wrapped.invokeAny(tasks, timeout, unit)

    override def shutdown(): Unit = {
      _collectorRegistration.cancel()
      _instruments.remove()
      wrapped.shutdown()
    }

    override def isShutdown: Boolean =
      wrapped.isShutdown

    private def wrapTasks[T](tasks: java.util.Collection[_ <: Callable[T]]): java.util.Collection[_ <: Callable[T]] = {
      val wrappedTasks = new util.LinkedList[Callable[T]]()
      val iterator = tasks.iterator()
      while (iterator.hasNext) {
        wrappedTasks.add(_callableWrapper.wrap(iterator.next()))
      }

      wrappedTasks
    }

    private def buildRunnableWrapper(): Runnable => Runnable = {
      if (settings.shouldTrackTimeInQueue) {
        if (settings.shouldPropagateContextOnSubmit)
          runnable => new TimingAndContextPropagatingRunnable(runnable)
        else
          runnable => new TimingRunnable(runnable)
      } else {
        if (settings.shouldPropagateContextOnSubmit)
          runnable => new ContextPropagationRunnable(runnable)
        else
          runnable => runnable
      }
    }

    private def buildCallableWrapper(): CallableWrapper = {
      if (settings.shouldTrackTimeInQueue) {
        if (settings.shouldPropagateContextOnSubmit)
          new TimingAndContextPropagatingCallableWrapper()
        else
          new TimingCallableWrapper
      } else {
        if (settings.shouldPropagateContextOnSubmit)
          new ContextPropagationCallableWrapper()
        else
          new CallableWrapper {
            override def wrap[T](callable: Callable[T]): Callable[T] = callable
          }
      }
    }

    private class TimingRunnable(runnable: Runnable) extends Runnable {
      private val _createdAt = System.nanoTime()

      override def run(): Unit = {
        _timeInQueueTimer.record(System.nanoTime() - _createdAt)
        runnable.run()
      }
    }

    private class TimingAndContextPropagatingRunnable(runnable: Runnable) extends Runnable {
      private val _createdAt = System.nanoTime()
      private val _context = Kamon.currentContext()

      override def run(): Unit = {
        _timeInQueueTimer.record(System.nanoTime() - _createdAt)

        val scope = Kamon.storeContext(_context)
        try { runnable.run() }
        finally { scope.close() }
      }
    }

    private class TimingCallableWrapper extends CallableWrapper {
      override def wrap[T](callable: Callable[T]): Callable[T] = new Callable[T] {
        val _createdAt = System.nanoTime()

        override def call(): T = {
          _timeInQueueTimer.record(System.nanoTime() - _createdAt)
          callable.call()
        }
      }
    }

    private class TimingAndContextPropagatingCallableWrapper extends CallableWrapper {
      override def wrap[T](callable: Callable[T]): Callable[T] = new Callable[T] {
        val _createdAt = System.nanoTime()
        val _context = Kamon.currentContext()

        override def call(): T = {
          _timeInQueueTimer.record(System.nanoTime() - _createdAt)

          val scope = Kamon.storeContext(_context)
          try { callable.call() }
          finally { scope.close() }
        }
      }
    }

    private class ContextPropagationRunnable(runnable: Runnable) extends Runnable {
      private val _context = Kamon.currentContext()

      override def run(): Unit = {
        val scope = Kamon.storeContext(_context)
        runnable.run()
        scope.close()
      }
    }

    private class ContextPropagationCallableWrapper extends CallableWrapper {
      override def wrap[T](callable: Callable[T]): Callable[T] = new Callable[T] {
        val _context = Kamon.currentContext()

        override def call(): T = {
          val scope = Kamon.storeContext(_context)
          try { callable.call() }
          finally { scope.close() }
        }
      }
    }
  }

  /**
    * Executor service wrapper for ScheduledThreadPool executors that keeps track of submitted and completed tasks.
    * Since tasks submitted to this type of executor are expected to be delayed for some time we are not explicitly
    * tracking the time-in-queue metric, nor allowing to perform context propagation (at least manually).
    *
    * The instruments used to track the pool's behavior are removed once the pool is shut down.
    */
  class InstrumentedScheduledThreadPoolExecutor(
    wrapped: ScheduledThreadPoolExecutor,
    name: String,
    extraTags: TagSet,
    scheduledActionName: String
  ) extends InstrumentedThreadPool(wrapped, name, extraTags, scheduledActionName, NoExtraSettings)
      with ScheduledExecutorService {

    override protected def executorType: String =
      "ScheduledThreadPoolExecutor"

    override def schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture[_] =
      wrapped.schedule(command, delay, unit)

    override def schedule[V](callable: Callable[V], delay: Long, unit: TimeUnit): ScheduledFuture[V] =
      wrapped.schedule(callable, delay, unit)

    override def scheduleAtFixedRate(
      command: Runnable,
      initialDelay: Long,
      period: Long,
      unit: TimeUnit
    ): ScheduledFuture[_] =
      wrapped.scheduleAtFixedRate(command, initialDelay, period, unit)

    override def scheduleWithFixedDelay(
      command: Runnable,
      initialDelay: Long,
      delay: Long,
      unit: TimeUnit
    ): ScheduledFuture[_] =
      wrapped.scheduleWithFixedDelay(command, initialDelay, delay, unit)
  }

  /**
    * Executor service wrapper for ForkJoin Pool executors that keeps track of submitted and completed tasks and
    * optionally tracks the time tasks spend waiting on the underlying executor service's queue. This instrumented
    * executor does some extra counting work (compared to the InstrumentedThreadPool class) because ForkJoin Pool
    * executors do not provide submitted and completed task counters.
    *
    * The instruments used to track the pool's behavior are removed once the pool is shut down.
    */
  class InstrumentedForkJoinPool(
    wrapped: ExecutorService,
    telemetryReader: ForkJoinPoolTelemetryReader,
    name: String,
    extraTags: TagSet,
    scheduledActionName: String,
    settings: Settings
  ) extends ExecutorService {

    private val _runnableWrapper = buildRunnableWrapper()
    private val _callableWrapper = buildCallableWrapper()
    private val _instruments = new ExecutorMetrics.ForkJoinPoolInstruments(name, extraTags)
    private val _timeInQueueTimer = _instruments.timeInQueue
    private val _submittedTasksCounter: LongAdder = new LongAdder
    private val _completedTasksCounter: LongAdder = new LongAdder
    private val _collectorRegistration = Kamon.addScheduledAction(
      scheduledActionName,
      Some(s"Updates health metrics for the ${name} thread pool every ${_sampleInterval.getSeconds} seconds"),
      new ScheduledAction {
        val submittedTasksSource = Counter.delta(() => _submittedTasksCounter.longValue())
        val completedTaskCountSource = Counter.delta(() => _completedTasksCounter.longValue())

        override def run(): Unit = {
          _instruments.poolMin.update(0d)
          _instruments.poolMax.update(telemetryReader.parallelism)
          _instruments.parallelism.update(telemetryReader.parallelism)
          _instruments.totalThreads.record(telemetryReader.poolSize)
          _instruments.activeThreads.record(telemetryReader.activeThreads)
          _instruments.queuedTasks.record(telemetryReader.queuedTasks)
          submittedTasksSource.accept(_instruments.submittedTasks)
          completedTaskCountSource.accept(_instruments.completedTasks)
        }

        override def stop(): Unit = {}
        override def reconfigure(newConfig: Config): Unit = {}

      },
      _sampleInterval
    )

    override def execute(command: Runnable): Unit = {
      _submittedTasksCounter.increment()
      wrapped.execute(_runnableWrapper(command))
    }

    override def submit(task: Runnable): Future[_] = {
      _submittedTasksCounter.increment()
      wrapped.submit(_runnableWrapper(task))
    }

    override def submit[T](task: Runnable, result: T): Future[T] = {
      _submittedTasksCounter.increment
      wrapped.submit(_runnableWrapper(task), result)
    }

    override def submit[T](task: Callable[T]): Future[T] = {
      _submittedTasksCounter.increment
      wrapped.submit(_callableWrapper.wrap(task))
    }

    override def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]]): java.util.List[Future[T]] = {
      _submittedTasksCounter.add(tasks.size())
      wrapped.invokeAll(wrapTasks(tasks).asInstanceOf[java.util.Collection[Callable[T]]])
    }

    override def invokeAll[T](
      tasks: java.util.Collection[_ <: Callable[T]],
      timeout: Long,
      unit: TimeUnit
    ): java.util.List[Future[T]] = {
      _submittedTasksCounter.add(tasks.size())
      wrapped.invokeAll(wrapTasks(tasks).asInstanceOf[java.util.Collection[Callable[T]]], timeout, unit)
    }

    override def isTerminated: Boolean =
      wrapped.isTerminated

    override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
      wrapped.awaitTermination(timeout, unit)

    override def shutdownNow(): java.util.List[Runnable] = {
      _collectorRegistration.cancel()
      _instruments.remove()
      wrapped.shutdownNow()
    }

    override def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]]): T =
      wrapped.invokeAny(tasks)

    override def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): T =
      wrapped.invokeAny(tasks, timeout, unit)

    override def shutdown(): Unit = {
      _collectorRegistration.cancel()
      _instruments.remove()
      wrapped.shutdown()
    }

    override def isShutdown: Boolean =
      wrapped.isShutdown

    private def wrapTasks[T](tasks: java.util.Collection[_ <: Callable[T]]): java.util.Collection[_ <: Callable[T]] = {
      val wrappedTasks = new util.LinkedList[Callable[T]]()
      val iterator = tasks.iterator()
      while (iterator.hasNext) {
        wrappedTasks.add(_callableWrapper.wrap(iterator.next()))
      }

      wrappedTasks
    }

    private def buildRunnableWrapper(): Runnable => Runnable = {
      if (settings.shouldTrackTimeInQueue) {
        if (settings.shouldPropagateContextOnSubmit)
          runnable => new TimingAndContextPropagatingRunnable(runnable)
        else
          runnable => new TimingRunnable(runnable)
      } else {
        if (settings.shouldPropagateContextOnSubmit)
          runnable => new ContextPropagationRunnable(runnable)
        else
          runnable => runnable
      }
    }

    private def buildCallableWrapper(): CallableWrapper = {
      if (settings.shouldTrackTimeInQueue) {
        if (settings.shouldPropagateContextOnSubmit)
          new TimingAndContextPropagatingCallableWrapper()
        else
          new TimingCallableWrapper
      } else {
        if (settings.shouldPropagateContextOnSubmit)
          new ContextPropagationCallableWrapper()
        else
          new CallableWrapper {
            override def wrap[T](callable: Callable[T]): Callable[T] = callable
          }
      }
    }

    private class TimingRunnable(runnable: Runnable) extends Runnable {
      private val _createdAt = System.nanoTime()

      override def run(): Unit = {
        _timeInQueueTimer.record(System.nanoTime() - _createdAt)
        try { runnable.run() }
        finally { _completedTasksCounter.increment() }
      }
    }

    private class TimingAndContextPropagatingRunnable(runnable: Runnable) extends Runnable {
      private val _createdAt = System.nanoTime()
      private val _context = Kamon.currentContext()

      override def run(): Unit = {
        _timeInQueueTimer.record(System.nanoTime() - _createdAt)

        val scope = Kamon.storeContext(_context)
        try { runnable.run() }
        finally {
          _completedTasksCounter.increment()
          scope.close()
        }
      }
    }

    private class TimingCallableWrapper extends CallableWrapper {
      override def wrap[T](callable: Callable[T]): Callable[T] = new Callable[T] {
        val _createdAt = System.nanoTime()

        override def call(): T = {
          _timeInQueueTimer.record(System.nanoTime() - _createdAt)
          try { callable.call() }
          finally { _completedTasksCounter.increment() }
        }
      }
    }

    private class TimingAndContextPropagatingCallableWrapper extends CallableWrapper {
      override def wrap[T](callable: Callable[T]): Callable[T] = new Callable[T] {
        val _createdAt = System.nanoTime()
        val _context = Kamon.currentContext()

        override def call(): T = {
          _timeInQueueTimer.record(System.nanoTime() - _createdAt)

          val scope = Kamon.storeContext(_context)
          try { callable.call() }
          finally {
            _completedTasksCounter.increment()
            scope.close()
          }
        }
      }
    }

    private class ContextPropagationRunnable(runnable: Runnable) extends Runnable {
      private val _context = Kamon.currentContext()

      override def run(): Unit = {
        val scope = Kamon.storeContext(_context)
        try { runnable.run() }
        finally {
          _completedTasksCounter.increment()
          scope.close()
        }
      }
    }

    private class ContextPropagationCallableWrapper extends CallableWrapper {
      override def wrap[T](callable: Callable[T]): Callable[T] = new Callable[T] {
        val _context = Kamon.currentContext()

        override def call(): T = {
          val scope = Kamon.storeContext(_context)
          try { callable.call() }
          finally {
            _completedTasksCounter.increment()
            scope.close()
          }
        }
      }
    }
  }

  /**
   * Wraps an Execution Context and its underlying ExecutorService, if known. The only purpose of wrapping is to
   * to provide a shutdown method that can be used to clear shutdown the underlying ExecutorService and remove all the
   * metrics related to it.
   */
  class InstrumentedExecutionContext(ec: ExecutionContext, val underlyingExecutor: Option[ExecutorService])
      extends ExecutionContext {
    override def execute(runnable: Runnable): Unit =
      ec.execute(runnable)

    override def reportFailure(cause: Throwable): Unit =
      ec.reportFailure(cause)

    def shutdown(): Unit = {
      underlyingExecutor.foreach(_.shutdown())
    }
  }
}
