package akka.kamon.instrumentation

import akka.actor.{ActorRef, ActorSystem, Cell}
import akka.kamon.instrumentation.ActorMonitors.{TrackedActor, TrackedRoutee}
import kamon.Kamon
import kamon.akka.Metrics
import kamon.akka.Metrics.{ActorGroupMetrics, ActorMetrics, RouterMetrics}
import org.aspectj.lang.ProceedingJoinPoint

trait ActorMonitor {
  def captureEnvelopeContext(): TimestampedContext
  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext): AnyRef
  def processFailure(failure: Throwable): Unit
  def cleanup(): Unit
}

object ActorMonitor {

  def createActorMonitor(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef, actorCellCreation: Boolean): ActorMonitor = {
    val cellInfo = CellInfo.cellInfoFor(cell, system, ref, parent, actorCellCreation)

    if (cellInfo.isRouter)
      ActorMonitors.ContextPropagationOnly
    else {
      if (cellInfo.isRoutee && cellInfo.isTracked)
        createRouteeMonitor(cellInfo)
      else
        createRegularActorMonitor(cellInfo)
    }
  }

  def createRegularActorMonitor(cellInfo: CellInfo): ActorMonitor = {
    if (cellInfo.isTracked || !cellInfo.trackingGroups.isEmpty) {
      val actorMetrics = if (cellInfo.isTracked) Some(Metrics.forActor(cellInfo.path)) else None
      new TrackedActor(actorMetrics, trackingGroupMetrics(cellInfo), cellInfo.actorCellCreation)
    } else {
      ActorMonitors.ContextPropagationOnly
    }
  }

  def createRouteeMonitor(cellInfo: CellInfo): ActorMonitor = {
    val routerMetrics = Metrics.forRouter(cellInfo.path)
    new TrackedRoutee(routerMetrics, trackingGroupMetrics(cellInfo), cellInfo.actorCellCreation)
  }

  private def trackingGroupMetrics(cellInfo: CellInfo): Seq[ActorGroupMetrics] = {
    cellInfo.trackingGroups.map { groupName =>
      Metrics.forGroup(groupName)
    }
  }
}

object ActorMonitors {

  val ContextPropagationOnly = new ActorMonitor {
    def captureEnvelopeContext(): TimestampedContext =
      TimestampedContext(0, Kamon.currentContext())

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext): AnyRef = {
      Kamon.withContext(envelopeContext.context) {
        pjp.proceed()
      }
    }

    def processFailure(failure: Throwable): Unit = {}
    def cleanup(): Unit = {}

  }

  class TrackedActor(actorMetrics: Option[ActorMetrics], groupMetrics: Seq[ActorGroupMetrics], actorCellCreation: Boolean)
      extends GroupMetricsTrackingActor(groupMetrics, actorCellCreation) {

    override def captureEnvelopeContext(): TimestampedContext = {
      actorMetrics.foreach { am =>
        am.mailboxSize.increment()
      }
      super.captureEnvelopeContext()
    }

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext): AnyRef = {
      val timestampBeforeProcessing = System.nanoTime()

      try {
        Kamon.withContext(envelopeContext.context) {
          pjp.proceed()
        }

      } finally {
        val timestampAfterProcessing = System.nanoTime()
        val timeInMailbox = timestampBeforeProcessing - envelopeContext.nanoTime
        val processingTime = timestampAfterProcessing - timestampBeforeProcessing

        actorMetrics.foreach { am =>
          am.processingTime.record(processingTime)
          am.timeInMailbox.record(timeInMailbox)
          am.mailboxSize.decrement()
        }
        recordProcessMetrics(processingTime, timeInMailbox)
      }
    }

    override def processFailure(failure: Throwable): Unit = {
      actorMetrics.foreach { am =>
        am.errors.increment()
      }
      super.processFailure(failure: Throwable)
    }

    override def cleanup(): Unit = {
      super.cleanup()
      actorMetrics.foreach(_.cleanup())
    }
  }

  class TrackedRoutee(routerMetrics: RouterMetrics, groupMetrics: Seq[ActorGroupMetrics], actorCellCreation: Boolean)
      extends GroupMetricsTrackingActor(groupMetrics, actorCellCreation) {

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext): AnyRef = {
      val timestampBeforeProcessing = System.nanoTime()

      try {
        Kamon.withContext(envelopeContext.context) {
          pjp.proceed()
        }

      } finally {
        val timestampAfterProcessing = System.nanoTime()
        val timeInMailbox = timestampBeforeProcessing - envelopeContext.nanoTime
        val processingTime = timestampAfterProcessing - timestampBeforeProcessing

        routerMetrics.processingTime.record(processingTime)
        routerMetrics.timeInMailbox.record(timeInMailbox)
        recordProcessMetrics(processingTime, timeInMailbox)
      }
    }

    override def processFailure(failure: Throwable): Unit = {
      routerMetrics.errors.increment()
      super.processFailure(failure)
    }

    override def cleanup(): Unit = {
      super.cleanup()
      routerMetrics.cleanup()
    }
  }

  abstract class GroupMetricsTrackingActor(groupMetrics: Seq[ActorGroupMetrics], actorCellCreation: Boolean) extends ActorMonitor {
    if (actorCellCreation) {
      groupMetrics.foreach { gm =>
        gm.members.increment()
      }
    }

    def captureEnvelopeContext(): TimestampedContext = {
      groupMetrics.foreach { gm =>
        gm.mailboxSize.increment()
      }

      TimestampedContext(System.nanoTime(), Kamon.currentContext())
    }

    def processFailure(failure: Throwable): Unit = {
      groupMetrics.foreach { gm =>
        gm.errors.increment()
      }
    }

    protected def recordProcessMetrics(processingTime: Long, timeInMailbox: Long): Unit = {
      groupMetrics.foreach { gm =>
        gm.processingTime.record(processingTime)
        gm.timeInMailbox.record(timeInMailbox)
        gm.mailboxSize.decrement()
      }
    }

    def cleanup(): Unit = {
      if (actorCellCreation) {
        groupMetrics.foreach { gm =>
          gm.members.decrement()
        }
      }
    }
  }
}
