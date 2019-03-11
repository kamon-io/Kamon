package akka.kamon.instrumentation

import akka.actor.Cell
import kamon.Kamon
import kamon.akka.Metrics
import kamon.akka.Metrics.RouterMetrics

trait RouterMonitor {
  def processFailure(failure: Throwable): Unit
  def cleanup(): Unit
  def routeeAdded(): Unit
  def routeeRemoved(): Unit

  //Kanela
  def processMessageStart():Long
  def processMessageEnd(timestampBeforeProcessing: Long): Unit
}

object RouterMonitor {

  def createRouterInstrumentation(cell: Cell): RouterMonitor = {
    val cellInfo = CellInfo.cellInfoFor(cell, cell.system, cell.self, cell.parent, false)

    if (cellInfo.isTracked)
      new MetricsOnlyRouterMonitor(
        Metrics.forRouter(
          cellInfo.path,
          cellInfo.systemName,
          cellInfo.dispatcherName,
          cellInfo.actorOrRouterClass.getName,
          cellInfo.routeeClass.map(_.getName).getOrElse("Unknown")
        )
      )
    else NoOpRouterMonitor
  }
}

object NoOpRouterMonitor extends RouterMonitor {
  def processFailure(failure: Throwable): Unit = {}
  def routeeAdded(): Unit = {}
  def routeeRemoved(): Unit = {}
  def cleanup(): Unit = {}

  def processMessageStart(): Long = 0L
  def processMessageEnd(timestampBeforeProcessing: Long): Unit = {}
}

class MetricsOnlyRouterMonitor(routerMetrics: RouterMetrics) extends RouterMonitor {

  def processMessageStart(): Long =
    Kamon.clock().nanos()

  def processMessageEnd(timestampBeforeProcessing: Long): Unit = {
    val timestampAfterProcessing = Kamon.clock().nanos()
    val routingTime = timestampAfterProcessing - timestampBeforeProcessing
    routerMetrics.routingTime.record(routingTime)
  }

  def processFailure(failure: Throwable): Unit = {}
  def routeeAdded(): Unit = {}
  def routeeRemoved(): Unit = {}
  def cleanup(): Unit = {
    routerMetrics.cleanup()
  }
}
