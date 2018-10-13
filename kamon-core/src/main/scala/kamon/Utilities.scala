package kamon

import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledThreadPoolExecutor}

import com.typesafe.config.Config
import kamon.util.{Clock, Filters, Matcher}

/**
  * Base utilities used by other Kamon components.
  */
trait Utilities { self: Configuration =>
  private val _clock = new Clock.Default()
  private val _scheduler = Executors.newScheduledThreadPool(schedulerPoolSize(self.config()), numberedThreadFactory("kamon-scheduler", daemon = false))
  @volatile private var _filters = Filters.fromConfig(self.config())

  self.onReconfigure(newConfig => {
    self._filters = Filters.fromConfig(newConfig)
    self._scheduler match {
      case stpe: ScheduledThreadPoolExecutor => stpe.setCorePoolSize(schedulerPoolSize(config))
      case _ => // cannot change the pool size on other unknown types.
    }
  })

  sys.addShutdownHook(() => _scheduler.shutdown())


  /**
    * Applies a filter to the given pattern. All filters are configured on the kamon.util.filters configuration section.
    *
    * @return true if the pattern matches at least one includes pattern and none of the excludes patterns in the filter.
    */
  def filter(filterName: String, pattern: String): Boolean =
    _filters.accept(filterName, pattern)

  /**
    * Retrieves a matcher for the given filter name. All filters are configured on the kamon.util.filters configuration
    * section.
    */
  def filter(filterName: String): Matcher =
    _filters.get(filterName)

  /**
    * Kamon's clock implementation.
    */
  def clock(): Clock =
    _clock

  /**
    * Scheduler to be used for Kamon-related tasks like updating gauges.
    */
  def scheduler(): ScheduledExecutorService =
    _scheduler



  private def schedulerPoolSize(config: Config): Int =
    config.getInt("kamon.scheduler-pool-size")
}
