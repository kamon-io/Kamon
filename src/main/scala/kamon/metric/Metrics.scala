package kamon.metric

import java.util.concurrent.TimeUnit
import com.codahale.metrics._
import akka.actor.ActorRef

object Metrics {
  val registry: MetricRegistry = new MetricRegistry

  val consoleReporter = ConsoleReporter.forRegistry(registry)
  val newrelicReporter = NewRelicReporter(registry)

  //newrelicReporter.start(5, TimeUnit.SECONDS)
  consoleReporter.build().start(60, TimeUnit.SECONDS)


  def deregister(fullName: String) = {
    registry.removeMatching(new MetricFilter {
      def matches(name: String, metric: Metric): Boolean = name.startsWith(fullName)
    })
  }
}

object MetricDirectory {
  def nameForDispatcher(actorSystem: String, dispatcher: String) = s"/ActorSystem/${actorSystem}/Dispatcher/${dispatcher}/"

  def nameForMailbox(actorSystem: String, actor: String) = s"/ActorSystem/$actorSystem/Actor/$actor/Mailbox"

  def nameForActor(actorRef: ActorRef) = actorRef.path.elements.fold("")(_ + "/" + _)
}
