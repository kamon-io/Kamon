package akka.kamon.instrumentation

import akka.actor.{ Cell, ActorRef, ActorSystem }
import akka.kamon.instrumentation.ActorMonitors.{ TrackedRoutee, TrackedActor }
import kamon.Kamon
import kamon.akka.{ RouterMetrics, ActorMetrics, ActorGroupConfig, ActorGroupMetrics }
import kamon.metric.Entity
import kamon.trace.{ EmptyTraceContext, Tracer }
import kamon.util.RelativeNanoTimestamp
import org.aspectj.lang.ProceedingJoinPoint

trait ActorMonitor {
  def captureEnvelopeContext(): EnvelopeContext
  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef
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
      val actorMetrics = if (cellInfo.isTracked) Some(Kamon.metrics.entity(ActorMetrics, cellInfo.entity)) else None
      new TrackedActor(cellInfo.entity, actorMetrics, trackingGroupMetrics(cellInfo), cellInfo.actorCellCreation)
    } else {
      ActorMonitors.ContextPropagationOnly
    }
  }

  def createRouteeMonitor(cellInfo: CellInfo): ActorMonitor = {
    def routerMetrics = Kamon.metrics.entity(RouterMetrics, cellInfo.entity)
    new TrackedRoutee(cellInfo.entity, routerMetrics, trackingGroupMetrics(cellInfo), cellInfo.actorCellCreation)
  }

  private def trackingGroupMetrics(cellInfo: CellInfo): List[ActorGroupMetrics] = {
    cellInfo.trackingGroups.map { groupName =>
      Kamon.metrics.entity(ActorGroupMetrics, Entity(groupName, ActorGroupMetrics.category))
    }
  }
}

object ActorMonitors {

  val ContextPropagationOnly = new ActorMonitor {
    def captureEnvelopeContext(): EnvelopeContext =
      EnvelopeContext(RelativeNanoTimestamp.now, Tracer.currentContext)

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef = {
      Tracer.withContext(envelopeContext.context) {
        pjp.proceed()
      }
    }

    def processFailure(failure: Throwable): Unit = {}
    def cleanup(): Unit = {}

  }

  class TrackedActor(val entity: Entity, actorMetrics: Option[ActorMetrics],
      groupMetrics: List[ActorGroupMetrics], actorCellCreation: Boolean)
      extends GroupMetricsTrackingActor(entity, groupMetrics, actorCellCreation) {

    override def captureEnvelopeContext(): EnvelopeContext = {
      actorMetrics.foreach { am =>
        am.mailboxSize.increment()
      }
      super.captureEnvelopeContext()
    }

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef = {
      val timestampBeforeProcessing = RelativeNanoTimestamp.now

      try {
        Tracer.withContext(envelopeContext.context) {
          pjp.proceed()
        }

      } finally {
        val timestampAfterProcessing = RelativeNanoTimestamp.now
        val timeInMailbox = timestampBeforeProcessing - envelopeContext.nanoTime
        val processingTime = timestampAfterProcessing - timestampBeforeProcessing

        actorMetrics.foreach { am =>
          am.processingTime.record(processingTime.nanos)
          am.timeInMailbox.record(timeInMailbox.nanos)
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
  }

  class TrackedRoutee(val entity: Entity, routerMetrics: RouterMetrics,
      groupMetrics: List[ActorGroupMetrics], actorCellCreation: Boolean)
      extends GroupMetricsTrackingActor(entity, groupMetrics, actorCellCreation) {

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef = {
      val timestampBeforeProcessing = RelativeNanoTimestamp.now

      try {
        Tracer.withContext(envelopeContext.context) {
          pjp.proceed()
        }

      } finally {
        val timestampAfterProcessing = RelativeNanoTimestamp.now
        val timeInMailbox = timestampBeforeProcessing - envelopeContext.nanoTime
        val processingTime = timestampAfterProcessing - timestampBeforeProcessing

        routerMetrics.processingTime.record(processingTime.nanos)
        routerMetrics.timeInMailbox.record(timeInMailbox.nanos)
        recordProcessMetrics(processingTime, timeInMailbox)
      }
    }

    override def processFailure(failure: Throwable): Unit = {
      routerMetrics.errors.increment()
      super.processFailure(failure)
    }
  }

  abstract class GroupMetricsTrackingActor(entity: Entity,
      groupMetrics: List[ActorGroupMetrics], actorCellCreation: Boolean) extends ActorMonitor {

    if (actorCellCreation) {
      groupMetrics.foreach { gm =>
        gm.actors.increment()
      }
    }

    def captureEnvelopeContext(): EnvelopeContext = {
      groupMetrics.foreach { gm =>
        gm.mailboxSize.increment()
      }
      EnvelopeContext(RelativeNanoTimestamp.now, Tracer.currentContext)
    }

    def processFailure(failure: Throwable): Unit = {
      groupMetrics.foreach { gm =>
        gm.errors.increment()
      }
    }

    protected def recordProcessMetrics(processingTime: RelativeNanoTimestamp, timeInMailbox: RelativeNanoTimestamp): Unit = {
      groupMetrics.foreach { gm =>
        gm.processingTime.record(processingTime.nanos)
        gm.timeInMailbox.record(timeInMailbox.nanos)
        gm.mailboxSize.decrement()
      }
    }

    def cleanup(): Unit = {
      Kamon.metrics.removeEntity(entity)
      if (actorCellCreation) {
        groupMetrics.foreach { gm =>
          gm.actors.decrement()
        }
      }
    }
  }
}
