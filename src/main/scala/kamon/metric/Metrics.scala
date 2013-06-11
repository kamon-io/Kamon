package kamon.metric

import java.util.concurrent.TimeUnit
import com.codahale.metrics._

object Metrics {
  val registry: MetricRegistry = new MetricRegistry

  val consoleReporter = ConsoleReporter.forRegistry(registry)
  val newrelicReporter = NewRelicReporter(registry)

  newrelicReporter.start(5, TimeUnit.SECONDS)
  //consoleReporter.build().start(5, TimeUnit.SECONDS)
}

object MetricDirectory {
  def nameForDispatcher(actorSystem: String, dispatcher: String) = s"/ActorSystem/${actorSystem}/Dispatcher/${dispatcher}/"
}
