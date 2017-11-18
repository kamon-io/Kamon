package akka.kamon.instrumentation

import akka.actor.{ActorRef, ActorSystem, Cell}
import akka.dispatch.Envelope
import akka.kamon.instrumentation.ActorMonitors.{TracedMonitor, TrackedActor, TrackedRoutee}
import kamon.Kamon
import kamon.akka.Metrics
import kamon.akka.Metrics.{ActorGroupMetrics, ActorMetrics, RouterMetrics}
import kamon.context.Context
import kamon.trace.Span
import org.aspectj.lang.ProceedingJoinPoint

trait ActorMonitor {
  def captureEnvelopeContext(): TimestampedContext
  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext, envelope: Envelope): AnyRef
  def processFailure(failure: Throwable): Unit
  def cleanup(): Unit
}


object ActorMonitor {

  def createActorMonitor(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef, actorCellCreation: Boolean): ActorMonitor = {
    val cellInfo = CellInfo.cellInfoFor(cell, system, ref, parent, actorCellCreation)

    Metrics.forSystem(system.name).activeActors.increment()

    val monitor = if (cellInfo.isRouter)
      ActorMonitors.ContextPropagationOnly(cellInfo)
    else {
      if (cellInfo.isRoutee && cellInfo.isTracked)
        createRouteeMonitor(cellInfo)
      else
        createRegularActorMonitor(cellInfo)
    }

    if(cellInfo.isTraced) new TracedMonitor(cellInfo, monitor) else monitor
  }

  def createRegularActorMonitor(cellInfo: CellInfo): ActorMonitor = {
    if (cellInfo.isTracked || !cellInfo.trackingGroups.isEmpty) {
      val actorMetrics = if (cellInfo.isTracked) Some(Metrics.forActor(cellInfo.path, cellInfo.systemName, cellInfo.dispatcherName, cellInfo.actorClass)) else None
      new TrackedActor(actorMetrics, trackingGroupMetrics(cellInfo), cellInfo.actorCellCreation, cellInfo)
    } else {
      ActorMonitors.ContextPropagationOnly(cellInfo)
    }
  }

  def createRouteeMonitor(cellInfo: CellInfo): ActorMonitor = {
    val routerMetrics = Metrics.forRouter(cellInfo.path, cellInfo.systemName, cellInfo.dispatcherName, cellInfo.actorClass)
    new TrackedRoutee(routerMetrics, trackingGroupMetrics(cellInfo), cellInfo.actorCellCreation, cellInfo)
  }

  private def trackingGroupMetrics(cellInfo: CellInfo): Seq[ActorGroupMetrics] = {
    cellInfo.trackingGroups.map { groupName =>
      Metrics.forGroup(groupName, cellInfo.systemName)
    }
  }
}

object ActorMonitors {

  class TracedMonitor(cellInfo: CellInfo, monitor: ActorMonitor) extends ActorMonitor {

    override def captureEnvelopeContext(): TimestampedContext = {
      val currCtx = Kamon.currentContext()
      val currSpan = currCtx.get(Span.ContextKey)
      val newSpan = Kamon.buildSpan(cellInfo.actorName)
        .asChildOf(currSpan)
        .start()
        .mark("enqueued")

      monitor.captureEnvelopeContext().copy(
        context = currCtx.withKey(Span.ContextKey, newSpan)
      )
    }

    override def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext, envelope: Envelope): AnyRef = {
      tagSpan(cellInfo, envelope, envelopeContext.context)
      val res = monitor.processMessage(pjp, envelopeContext, envelope)
      finishSpan(envelopeContext.context)
      res
    }

    override def processFailure(failure: Throwable): Unit = monitor.processFailure(failure)

    override def cleanup(): Unit = monitor.cleanup()

    private def tagSpan(cellInfo: CellInfo, envelope: Envelope, context: Context) = {
      val span = context.get(Span.ContextKey)
      val messageClass = envelope.message.getClass.getSimpleName

      span.setOperationName(s"${cellInfo.actorName}.$messageClass")

      span.mark("dequeued")

      span.tag("path", cellInfo.path)
      span.tag("system", cellInfo.systemName)
      span.tag("actor-class", cellInfo.actorClass)
      span.tag("message-class", messageClass)
    }

    private def finishSpan(context: Context) = {
      val span = context.get(Span.ContextKey)
      span.mark("processed")
      span.finish()
    }

  }


  def ContextPropagationOnly(cellInfo: CellInfo) = new ActorMonitor {
    def captureEnvelopeContext(): TimestampedContext = TimestampedContext(0, Kamon.currentContext())//tracedContext(cellInfo.isTracked, cellInfo.traced, Kamon.currentContext())

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext, envelope: Envelope): AnyRef = {
      Metrics.forSystem(cellInfo.systemName).processedMessagesByNonTracked.increment()

      Kamon.withContext(envelopeContext.context) {
        pjp.proceed()
      }
    }

    def processFailure(failure: Throwable): Unit = {}
    def cleanup(): Unit = {
      Metrics.forSystem(cellInfo.systemName).activeActors.decrement()
    }
  }



  class TrackedActor(actorMetrics: Option[ActorMetrics], groupMetrics: Seq[ActorGroupMetrics], actorCellCreation: Boolean, cellInfo: CellInfo)
      extends GroupMetricsTrackingActor(groupMetrics, actorCellCreation, cellInfo) {

    override def captureEnvelopeContext(): TimestampedContext = {
      actorMetrics.foreach { am =>
        am.mailboxSize.increment()
      }
      super.captureEnvelopeContext()
    }

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext, envelope: Envelope): AnyRef = {
      val timestampBeforeProcessing = System.nanoTime()
      Metrics.forSystem(cellInfo.systemName).processedMessagesByTracked.increment()

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

  class TrackedRoutee(routerMetrics: RouterMetrics, groupMetrics: Seq[ActorGroupMetrics], actorCellCreation: Boolean, cellInfo: CellInfo)
      extends GroupMetricsTrackingActor(groupMetrics, actorCellCreation, cellInfo) {

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext, envelope: Envelope): AnyRef = {
      val timestampBeforeProcessing = System.nanoTime()
      Metrics.forSystem(cellInfo.systemName).processedMessagesByTracked.increment()

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

  abstract class GroupMetricsTrackingActor(groupMetrics: Seq[ActorGroupMetrics], actorCellCreation: Boolean, cellInfo: CellInfo) extends ActorMonitor {
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
      Metrics.forSystem(cellInfo.systemName).activeActors.decrement()
      if (actorCellCreation) {
        groupMetrics.foreach { gm =>
          gm.members.decrement()
        }
      }
    }
  }
}
