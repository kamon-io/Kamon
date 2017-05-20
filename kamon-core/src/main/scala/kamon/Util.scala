package kamon

import kamon.metric.EntityFilter

/**
  * Useful classes for Kamon and submodules.
  *
  */
trait Util {

  /**
    * @return Currently configured entity filters.
    */
  def entityFilter: EntityFilter
}
