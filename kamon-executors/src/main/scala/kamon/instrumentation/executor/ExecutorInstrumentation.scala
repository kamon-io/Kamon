package kamon.instrumentation.executor

import java.time.Duration
import java.util
import java.util.concurrent.{Callable, Executor, ExecutorService, Future, ThreadPoolExecutor, TimeUnit, ForkJoinPool => JavaForkJoinPool}

import com.typesafe.config.Config
import kamon.Kamon
import kamon.jsr166.LongAdder
import kamon.metric.Counter
import kamon.tag.TagSet
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
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
    *   * type: set to "tpe" for ThreadPoolExecutor executors or "fjp" for ForkJoinPool executors.
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
    *   * type: set to "tpe" for ThreadPoolExecutor executors or "fjp" for ForkJoinPool executors.
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrumentExecutionContext(executionContext: ExecutionContext, name: String): ExecutionContextExecutorService =
    instrumentExecutionContext(executionContext, name, TagSet.Empty, DefaultSettings)

  /**
    * Creates a new instrumented ExecutorService that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala) and optionally, track the
    * time spent by each task on the wrapped executor's queue.
    *
    * All metrics related to the instrumented service will have the following tags:
    *
    *   * name: set to the provided name parameter.
    *   * type: set to "tpe" for ThreadPoolExecutor executors or "fjp" for ForkJoinPool executors.
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrument(executor: ExecutorService, name: String, options: Settings): ExecutorService =
    instrument(executor, name, TagSet.Empty, options)

  /**
    * Creates a new instrumented ExecutionContext that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala) and optionally, track the
    * time spent by each task on the wrapped executor's queue.
    *
    * All metrics related to the instrumented service will have the following tags:
    *
    *   * name: set to the provided name parameter.
    *   * type: set to "tpe" for ThreadPoolExecutor executors or "fjp" for ForkJoinPool executors.
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrumentExecutionContext(executionContext: ExecutionContext, name: String, options: Settings): ExecutionContextExecutorService =
    instrumentExecutionContext(executionContext, name, TagSet.Empty, options)

  /**
    * Creates a new instrumented ExecutorService that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala). All metrics related to the
    * instrumented service will have the following tags:
    *
    *   * all of the provided extraTags (take into account that any "name" or "type" tags will be overwritten.
    *   * name: set to the provided name parameter.
    *   * type: set to "tpe" for ThreadPoolExecutor executors or "fjp" for ForkJoinPool executors.
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
    *   * type: set to "tpe" for ThreadPoolExecutor executors or "fjp" for ForkJoinPool executors.
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrumentExecutionContext(executionContext: ExecutionContext, name: String, extraTags: TagSet): ExecutionContextExecutorService =
    instrumentExecutionContext(executionContext, name, extraTags, DefaultSettings)

  /**
    * Creates a new instrumented ExecutorService that wraps the provided one. The instrumented executor will track
    * metrics for ThreadPoolExecutor and ForkJoinPool instances (both from Java and Scala) and optionally, track the
    * time spent by each task on the wrapped executor's queue.
    *
    * All metrics related to the instrumented service will have the following tags:
    *   * all of the provided extraTags (take into account that any "name" or "type" tags will be overwritten.
    *   * name: set to the provided name parameter.
    *   * type: set to "tpe" for ThreadPoolExecutor executors or "fjp" for ForkJoinPool executors.
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrument(executor: ExecutorService, name: String, extraTags: TagSet, options: Settings): ExecutorService = {
    executor match {
      case es: ExecutorService if isWrapper(es) => instrument(unwrap(es), name, extraTags, options)
      case tpe: ThreadPoolExecutor  => new InstrumentedThreadPool(tpe, name, extraTags, options)
      case jfjp: JavaForkJoinPool   => new InstrumentedForkJoinPool(jfjp, ForkJoinPoolTelemetryReader.forJava(jfjp), name, extraTags, options)
      case sfjp: ScalaForkJoinPool  => new InstrumentedForkJoinPool(sfjp, ForkJoinPoolTelemetryReader.forScala(sfjp), name, extraTags, options)
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
    *   * type: set to "tpe" for ThreadPoolExecutor executors or "fjp" for ForkJoinPool executors.
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrumentExecutionContext(executionContext: ExecutionContext, name: String, extraTags: TagSet,
      options: Settings): ExecutionContextExecutorService = {

    val executor = unwrapExecutionContext(executionContext)
    val instrumentedExecutor = instrument(executor, name, extraTags, options)

    ExecutionContext.fromExecutorService(instrumentedExecutor)
  }

  /**
    * Creates a new instrumented ExecutorService that wraps the provided one, assuming that the wrapped executor is a
    * form of ForkJoinPool implementation. The instrumented executor will track all the common pool metrics and
    * optionally, track the time spent by each task on the wrapped executor's queue.
    *
    * All metrics related to the instrumented service will have the following tags:
    *   * all of the provided extraTags (take into account that any "name" or "type" tags will be overwritten.
    *   * name: set to the provided name parameter.
    *   * type: set to "tpe" for ThreadPoolExecutor executors or "fjp" for ForkJoinPool executors.
    *
    * Once the returned executor is shutdown, all related metric instruments will be removed.
    */
  def instrument(executor: ExecutorService, telemetryReader: ForkJoinPoolTelemetryReader, name: String, extraTags: TagSet,
      options: Settings): ExecutorService = {
    new InstrumentedForkJoinPool(executor, telemetryReader, name, extraTags, options)
  }

  /**
    * We do not perform context propagation on submit by default because the automatic instrumentation provided by
    * this module should ensure that all interesting Runnable/Callable implementations capture a Context instance.
    * Furthermore, in many situations the current Context when a Runnable/Callable is created is different from the
    * current context when it is submitted for execution and in most situations it is safer to assume that all
    * Runnable/Callable should capture the current Context at the instant when they are created, not when submitted.
    */
  val DefaultSettings = new Settings(shouldTrackTimeInQueue = true, shouldPropagateContextOnSubmit = false)

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


  private val _delegatedExecutorClass = Class.forName("java.util.concurrent.Executors$DelegatedExecutorService")
  private val _finalizableDelegatedClass = Class.forName("java.util.concurrent.Executors$FinalizableDelegatedExecutorService")
  private val _delegateScheduledClass = Class.forName("java.util.concurrent.Executors$DelegatedScheduledExecutorService")
  private val _delegatedExecutorField = {
    val field = _delegatedExecutorClass.getDeclaredField("e")
    field.setAccessible(true)
    field
  }

  private val _executionContextExecutorField = {
    val field = Class.forName("scala.concurrent.impl.ExecutionContextImpl").getDeclaredField("executor")
    field.setAccessible(true)
    field
  }

  private def isAssignableTo(executor: ExecutorService, expectedClass: Class[_]): Boolean =
    expectedClass.isAssignableFrom(executor.getClass)

  private def isWrapper(executor: ExecutorService): Boolean = {
    isAssignableTo(executor, _delegatedExecutorClass) ||
    isAssignableTo(executor, _finalizableDelegatedClass) ||
    isAssignableTo(executor, _delegateScheduledClass)
  }

  private def unwrap(delegatedExecutor: ExecutorService): ExecutorService =
    _delegatedExecutorField.get(delegatedExecutor).asInstanceOf[ExecutorService]

  private def unwrapExecutionContext(executionContext: ExecutionContext): ExecutorService =
    _executionContextExecutorField.get(executionContext).asInstanceOf[ExecutorService]

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
      override def activeThreads: Int  = pool.getActiveThreadCount
      override def poolSize: Int       = pool.getPoolSize
      override def queuedTasks: Int    = pool.getQueuedSubmissionCount
      override def parallelism: Int    = pool.getParallelism
    }

    def forScala(pool: ScalaForkJoinPool): ForkJoinPoolTelemetryReader = new ForkJoinPoolTelemetryReader {
      override def activeThreads: Int  = pool.getActiveThreadCount
      override def poolSize: Int       = pool.getPoolSize
      override def queuedTasks: Int    = pool.getQueuedSubmissionCount
      override def parallelism: Int    = pool.getParallelism
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
  class InstrumentedThreadPool(wrapped: ThreadPoolExecutor, name: String, extraTags: TagSet, options: Settings)
      extends ExecutorService {

    private val _runableWrapper = buildRunnableWrapper()
    private val _callableWrapper = buildCallableWrapper()
    private val _instruments = new ExecutorMetrics.ThreadPoolInstruments(name, extraTags)
    private val _timeInQueueTimer = _instruments.timeInQueue
    private val _sampler = Kamon.scheduler().scheduleAtFixedRate(new Runnable {
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
    }, _sampleInterval.toMillis, _sampleInterval.toMillis, TimeUnit.MILLISECONDS)

    override def execute(command: Runnable): Unit =
      wrapped.execute(_runableWrapper(command))

    override def submit(task: Runnable): Future[_] =
      wrapped.submit(_runableWrapper(task))

    override def submit[T](task: Runnable, result: T): Future[T] =
      wrapped.submit(_runableWrapper(task), result)

    override def submit[T](task: Callable[T]): Future[T] =
      wrapped.submit(_callableWrapper.wrap(task))

    override def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]]): java.util.List[Future[T]] =
      wrapped.invokeAll(wrapTasks(tasks).asInstanceOf[java.util.Collection[Callable[T]]])

    override def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit):  java.util.List[Future[T]] =
      wrapped.invokeAll(wrapTasks(tasks).asInstanceOf[java.util.Collection[Callable[T]]], timeout, unit)

    override def isTerminated: Boolean =
      wrapped.isTerminated

    override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
      wrapped.awaitTermination(timeout, unit)

    override def shutdownNow(): java.util.List[Runnable] = {
      _sampler.cancel(false)
      _instruments.remove()
      wrapped.shutdownNow()
    }

    override def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]]): T =
      wrapped.invokeAny(tasks)

    override def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): T =
      wrapped.invokeAny(tasks, timeout, unit)

    override def shutdown(): Unit = {
      _sampler.cancel(false)
      _instruments.remove()
      wrapped.shutdown()
    }

    override def isShutdown: Boolean =
      wrapped.isShutdown

    private def wrapTasks[T](tasks: java.util.Collection[_ <: Callable[T]]): java.util.Collection[_ <: Callable[T]] = {
      val wrappedTasks = new util.LinkedList[Callable[T]]()
      val iterator = tasks.iterator()
      while(iterator.hasNext) {
        wrappedTasks.add(_callableWrapper.wrap(iterator.next()))
      }

      wrappedTasks
    }

    private def buildRunnableWrapper(): Runnable => Runnable = {
      if(options.shouldTrackTimeInQueue) {
        if (options.shouldPropagateContextOnSubmit)
          runnable => new TimingAndContextPropagatingRunnable(runnable)
        else
          runnable => new TimingRunnable(runnable)
      } else {
        if(options.shouldPropagateContextOnSubmit)
          runnable => new ContextPropagationRunnable(runnable)
        else
          runnable => runnable
      }
    }

    private def buildCallableWrapper(): CallableWrapper = {
      if(options.shouldTrackTimeInQueue) {
        if (options.shouldPropagateContextOnSubmit)
          new TimingAndContextPropagatingCallableWrapper()
        else
          new TimingCallableWrapper
      } else {
        if (options.shouldPropagateContextOnSubmit)
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

        val scope = Kamon.store(_context)
        try { runnable.run() } finally { scope.close() }
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

          val scope = Kamon.store(_context)
          try { callable.call() } finally { scope.close() }
        }
      }
    }

    private class ContextPropagationRunnable(runnable: Runnable) extends Runnable {
      private val _context = Kamon.currentContext()

      override def run(): Unit = {
        val scope = Kamon.store(_context)
        runnable.run()
        scope.close()
      }
    }

    private class ContextPropagationCallableWrapper extends CallableWrapper {
      override def wrap[T](callable: Callable[T]): Callable[T] = new Callable[T] {
        val _context = Kamon.currentContext()

        override def call(): T = {
          val scope = Kamon.store(_context)
          try { callable.call() } finally { scope.close() }
        }
      }
    }
  }


  /**
    * Executor service wrapper for ForkJoin Pool executors that keeps track of submitted and completed tasks and
    * optionally tracks the time tasks spend waiting on the underlying executor service's queue. This instrumented
    * executor does some extra counting work (compared to the InstrumentedThreadPool class) because ForkJoin Pool
    * executors do not provide submitted and completed task counters.
    *
    * The instruments used to track the pool's behavior are removed once the pool is shut down.
    */
  class InstrumentedForkJoinPool(wrapped: ExecutorService, telemetryReader: ForkJoinPoolTelemetryReader, name: String,
      extraTags: TagSet, options: Settings) extends ExecutorService {

    private val _runableWrapper = buildRunnableWrapper()
    private val _callableWrapper = buildCallableWrapper()
    private val _instruments = new ExecutorMetrics.ForkJoinPoolInstruments(name, extraTags)
    private val _timeInQueueTimer = _instruments.timeInQueue
    private val _submittedTasksCounter: LongAdder = new LongAdder
    private val _completedTasksCounter: LongAdder = new LongAdder
    private val _sampler = Kamon.scheduler().scheduleAtFixedRate(new Runnable {
      val submittedTasksSource = Counter.delta(() => _submittedTasksCounter.longValue())
      val completedTaskCountSource = Counter.delta(() => _completedTasksCounter.longValue())

      override def run(): Unit = {
        _instruments.poolMin.update(0D)
        _instruments.poolMax.update(telemetryReader.parallelism)
        _instruments.parallelism.update(telemetryReader.parallelism)
        _instruments.totalThreads.record(telemetryReader.poolSize)
        _instruments.activeThreads.record(telemetryReader.activeThreads)
        _instruments.queuedTasks.record(telemetryReader.queuedTasks)
        submittedTasksSource.accept(_instruments.submittedTasks)
        completedTaskCountSource.accept(_instruments.completedTasks)
      }
    }, _sampleInterval.toMillis, _sampleInterval.toMillis, TimeUnit.MILLISECONDS)

    override def execute(command: Runnable): Unit = {
      _submittedTasksCounter.increment()
      wrapped.execute(_runableWrapper(command))
    }

    override def submit(task: Runnable): Future[_] = {
      _submittedTasksCounter.increment()
      wrapped.submit(_runableWrapper(task))
    }

    override def submit[T](task: Runnable, result: T): Future[T] = {
      _submittedTasksCounter.increment
      wrapped.submit(_runableWrapper(task), result)
    }

    override def submit[T](task: Callable[T]): Future[T] = {
      _submittedTasksCounter.increment
      wrapped.submit(_callableWrapper.wrap(task))
    }

    override def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]]): java.util.List[Future[T]] = {
      _submittedTasksCounter.add(tasks.size())
      wrapped.invokeAll(wrapTasks(tasks).asInstanceOf[java.util.Collection[Callable[T]]])
    }

    override def invokeAll[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit):  java.util.List[Future[T]] = {
      _submittedTasksCounter.add(tasks.size())
      wrapped.invokeAll(wrapTasks(tasks).asInstanceOf[java.util.Collection[Callable[T]]], timeout, unit)
    }

    override def isTerminated: Boolean =
      wrapped.isTerminated

    override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
      wrapped.awaitTermination(timeout, unit)

    override def shutdownNow(): java.util.List[Runnable] = {
      _sampler.cancel(false)
      _instruments.remove()
      wrapped.shutdownNow()
    }

    override def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]]): T =
      wrapped.invokeAny(tasks)

    override def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): T =
      wrapped.invokeAny(tasks, timeout, unit)

    override def shutdown(): Unit = {
      _sampler.cancel(false)
      _instruments.remove()
      wrapped.shutdown()
    }

    override def isShutdown: Boolean =
      wrapped.isShutdown

    private def wrapTasks[T](tasks: java.util.Collection[_ <: Callable[T]]): java.util.Collection[_ <: Callable[T]] = {
      val wrappedTasks = new util.LinkedList[Callable[T]]()
      val iterator = tasks.iterator()
      while(iterator.hasNext) {
        wrappedTasks.add(_callableWrapper.wrap(iterator.next()))
      }

      wrappedTasks
    }

    private def buildRunnableWrapper(): Runnable => Runnable = {
      if(options.shouldTrackTimeInQueue) {
        if (options.shouldPropagateContextOnSubmit)
          runnable => new TimingAndContextPropagatingRunnable(runnable)
        else
          runnable => new TimingRunnable(runnable)
      } else {
        if(options.shouldPropagateContextOnSubmit)
          runnable => new ContextPropagationRunnable(runnable)
        else
          runnable => runnable
      }
    }

    private def buildCallableWrapper(): CallableWrapper = {
      if(options.shouldTrackTimeInQueue) {
        if (options.shouldPropagateContextOnSubmit)
          new TimingAndContextPropagatingCallableWrapper()
        else
          new TimingCallableWrapper
      } else {
        if (options.shouldPropagateContextOnSubmit)
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
        try { runnable.run() } finally { _completedTasksCounter.increment() }
      }
    }

    private class TimingAndContextPropagatingRunnable(runnable: Runnable) extends Runnable {
      private val _createdAt = System.nanoTime()
      private val _context = Kamon.currentContext()

      override def run(): Unit = {
        _timeInQueueTimer.record(System.nanoTime() - _createdAt)

        val scope = Kamon.store(_context)
        try { runnable.run() } finally {
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
          try { callable.call() } finally { _completedTasksCounter.increment() }
        }
      }
    }

    private class TimingAndContextPropagatingCallableWrapper extends CallableWrapper {
      override def wrap[T](callable: Callable[T]): Callable[T] = new Callable[T] {
        val _createdAt = System.nanoTime()
        val _context = Kamon.currentContext()

        override def call(): T = {
          _timeInQueueTimer.record(System.nanoTime() - _createdAt)

          val scope = Kamon.store(_context)
          try { callable.call() } finally {
            _completedTasksCounter.increment()
            scope.close()
          }
        }
      }
    }

    private class ContextPropagationRunnable(runnable: Runnable) extends Runnable {
      private val _context = Kamon.currentContext()

      override def run(): Unit = {
        val scope = Kamon.store(_context)
        try { runnable.run() } finally {
          _completedTasksCounter.increment()
          scope.close()
        }
      }
    }

    private class ContextPropagationCallableWrapper extends CallableWrapper {
      override def wrap[T](callable: Callable[T]): Callable[T] = new Callable[T] {
        val _context = Kamon.currentContext()

        override def call(): T = {
          val scope = Kamon.store(_context)
          try { callable.call() } finally {
            _completedTasksCounter.increment()
            scope.close()
          }
        }
      }
    }
  }
}
