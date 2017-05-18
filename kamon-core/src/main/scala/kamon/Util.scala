package kamon

import kamon.util.EntityFilter

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
