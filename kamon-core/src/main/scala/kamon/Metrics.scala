package kamon

import kamon.metric._

/**
  * Exposes all metric building APIs using a built-in, globally shared metric registry.
  */
trait Metrics extends MetricBuilding { self: Configuration with Utilities =>
  protected val _metricRegistry = new MetricRegistry(self.config(), self.scheduler(), self.clock())

  /**
    * Metric registry from which all metric-building APIs will draw instances. For more details on the entire set of
    * exposes APIs please refer to [[kamon.metric.MetricBuilding.]]
    */
  protected def registry(): MetricRegistry =
    _metricRegistry

}
