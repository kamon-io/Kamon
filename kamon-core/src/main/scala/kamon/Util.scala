package kamon

import kamon.util.{Clock, EntityFilter}

/**
  * Useful classes for Kamon and submodules.
  *
  */
trait Util {
  /**
    * @return The Clock instance used by Kamon for timestamps and latency measurements.
    */
  def clock: Clock

  /**
    * @return Currently configured entity filters.
    */
  def entityFilter: EntityFilter
}
