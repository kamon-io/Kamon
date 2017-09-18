package akka.kamon.instrumentation

import akka.actor.Cell
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
      new MetricsOnlyRouterMonitor(Metrics.forRouter(cellInfo.path, cellInfo.systemName, cellInfo.dispatcherName))
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
    val timestampBeforeProcessing = System.nanoTime()

    try {
      pjp.proceed()
    } finally {
      val timestampAfterProcessing = System.nanoTime()
      val routingTime = timestampAfterProcessing - timestampBeforeProcessing

      routerMetrics.routingTime.record(routingTime)
    }
  }

  def processFailure(failure: Throwable): Unit = {}
  def routeeAdded(): Unit = {}
  def routeeRemoved(): Unit = {}
  def cleanup(): Unit = routerMetrics.cleanup()
}
