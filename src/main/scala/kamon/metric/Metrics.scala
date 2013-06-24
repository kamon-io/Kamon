package kamon.metric

import java.util.concurrent.TimeUnit
import com.codahale.metrics._
import akka.actor.ActorRef

trait MetricDepot {
  def include(name: String, metric: Metric): Unit
  def exclude(name: String): Unit
}



object Metrics extends MetricDepot {
  val registry: MetricRegistry = new MetricRegistry

  val consoleReporter = ConsoleReporter.forRegistry(registry).convertDurationsTo(TimeUnit.NANOSECONDS)
  val newrelicReporter = NewRelicReporter(registry)

  //newrelicReporter.start(5, TimeUnit.SECONDS)
  consoleReporter.build().start(10, TimeUnit.SECONDS)


  def include(name: String, metric: Metric) = registry.register(name, metric)

  def exclude(name: String) = {
    registry.removeMatching(new MetricFilter {
      def matches(name: String, metric: Metric): Boolean = name.startsWith(name)
    })
  }



  def deregister(fullName: String) = {
    registry.removeMatching(new MetricFilter {
      def matches(name: String, metric: Metric): Boolean = name.startsWith(fullName)
    })
  }
}

object MetricDirectory {
  def nameForDispatcher(actorSystem: String, dispatcher: String) = s"/ActorSystem/${actorSystem}/Dispatcher/${dispatcher}/"

  def nameForMailbox(actorSystem: String, actor: String) = s"/ActorSystem/$actorSystem/Actor/$actor/Mailbox"

  def nameForActor(actorRef: ActorRef) = actorRef.path.elements.mkString("/")

  def shouldInstrument(actorSystem: String): Boolean = !actorSystem.startsWith("kamon")


  def shouldInstrumentActor(actorPath: String): Boolean = {
    !(actorPath.isEmpty || actorPath.startsWith("system"))
  }




}


