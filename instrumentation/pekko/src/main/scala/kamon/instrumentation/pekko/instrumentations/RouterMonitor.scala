package kamon.instrumentation.pekko.instrumentations

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import kamon.Kamon
import kamon.instrumentation.pekko.PekkoInstrumentation.TrackRouterFilterName
import kamon.instrumentation.pekko.PekkoMetrics
import kamon.instrumentation.pekko.PekkoMetrics.RouterInstruments

/**
  * Exposes the necessary callbacks for instrumenting a Router.
  */
trait RouterMonitor {

  /**
    * Signals that a routee has been added to the router.
    */
  def routeeAdded(): Unit

  /**
    * Signals that a routee has been removed from the router.
    */
  def routeeRemoved(): Unit

  /**
    * Callback executed with processing message starts. In a router, the processing time is actually just about routing
    * the message to the right routee and there is no mailbox involved.
    */
  def processMessageStart(): Long

  /**
    * Callback executed when the message has been routed.
    */
  def processMessageEnd(timestampBeforeProcessing: Long): Unit

  /**
    * Callback executed when a router fails to route a message.
    */
  def processFailure(failure: Throwable): Unit

  /**
    * Cleans up all resources used by the router monitor.
    */
  def cleanup(): Unit
}

object RouterMonitor {

  def from(actorCell: Any, ref: ActorRef, parent: ActorRef, system: ActorSystem): RouterMonitor = {
    val cell = ActorCellInfo.from(actorCell, ref, parent, system)

    if (Kamon.filter(TrackRouterFilterName).accept(cell.path))
      new MetricsOnlyRouterMonitor(
        PekkoMetrics.forRouter(
          cell.path,
          cell.systemName,
          cell.dispatcherName,
          cell.actorOrRouterClass,
          cell.routeeClass.filterNot(ActorCellInfo.isTyped).map(_.getName).getOrElse("Unknown")
        )
      )
    else NoOpRouterMonitor
  }

  /**
    * Router monitor that doesn't perform any actions.
    */
  object NoOpRouterMonitor extends RouterMonitor {
    override def routeeAdded(): Unit = {}
    override def routeeRemoved(): Unit = {}
    override def processMessageStart(): Long = 0L
    override def processMessageEnd(timestampBeforeProcessing: Long): Unit = {}
    override def processFailure(failure: Throwable): Unit = {}
    override def cleanup(): Unit = {}
  }

  /**
    * Router monitor that tracks routing metrics for the router.
    */
  class MetricsOnlyRouterMonitor(routerMetrics: RouterInstruments) extends RouterMonitor {
    private val _clock = Kamon.clock()

    override def routeeAdded(): Unit = {}
    override def routeeRemoved(): Unit = {}
    override def processFailure(failure: Throwable): Unit = {}

    override def processMessageStart(): Long =
      _clock.nanos()

    override def processMessageEnd(timestampBeforeProcessing: Long): Unit = {
      val timestampAfterProcessing = _clock.nanos()
      val routingTime = timestampAfterProcessing - timestampBeforeProcessing
      routerMetrics.routingTime.record(routingTime)
    }

    override def cleanup(): Unit =
      routerMetrics.remove()
  }
}
