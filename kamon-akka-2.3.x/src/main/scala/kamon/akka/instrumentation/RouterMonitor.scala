package akka.kamon.instrumentation

import akka.actor.Cell
import kamon.Kamon
import kamon.akka.Metrics.RouterMetrics
import kamon.akka.Metrics
import org.aspectj.lang.ProceedingJoinPoint

trait RouterMonitor {
  def processMessage(pjp: ProceedingJoinPoint): AnyRef
  def processFailure(failure: Throwable): Unit
  def cleanup(): Unit

  def routeeAdded(): Unit
  def routeeRemoved(): Unit
}

object RouterMonitor {

  def createRouterInstrumentation(cell: Cell): RouterMonitor = {
    val cellInfo = CellInfo.cellInfoFor(cell, cell.system, cell.self, cell.parent, false)

    if (cellInfo.isTracked)
      new MetricsOnlyRouterMonitor(Metrics.forRouter(cellInfo.path, cellInfo.systemName, cellInfo.dispatcherName, cellInfo.actorClass.getName))
    else NoOpRouterMonitor
  }
}

object NoOpRouterMonitor extends RouterMonitor {
  def processMessage(pjp: ProceedingJoinPoint): AnyRef = pjp.proceed()
  def processFailure(failure: Throwable): Unit = {}
  def routeeAdded(): Unit = {}
  def routeeRemoved(): Unit = {}
  def cleanup(): Unit = {}
}

class MetricsOnlyRouterMonitor(routerMetrics: RouterMetrics) extends RouterMonitor {

  def processMessage(pjp: ProceedingJoinPoint): AnyRef = {
    val timestampBeforeProcessing = Kamon.clock().nanos()

    try {
      pjp.proceed()
    } finally {
      val timestampAfterProcessing = Kamon.clock().nanos()
      val routingTime = timestampAfterProcessing - timestampBeforeProcessing

      routerMetrics.routingTime.record(routingTime)
    }
  }

  def processFailure(failure: Throwable): Unit = {}
  def routeeAdded(): Unit = {}
  def routeeRemoved(): Unit = {}
  def cleanup(): Unit = routerMetrics.cleanup()
}
