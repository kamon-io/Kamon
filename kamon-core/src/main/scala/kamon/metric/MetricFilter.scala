package kamon.metric

object MetricFilter {
  def actorSystem(system: String): Boolean = !system.startsWith("kamon")
  def actor(path: String, system: String): Boolean = true
}
