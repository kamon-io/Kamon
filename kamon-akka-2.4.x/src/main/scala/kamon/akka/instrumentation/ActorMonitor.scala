package akka.kamon.instrumentation

import akka.actor.{ActorRef, ActorSystem, Cell}
import akka.dispatch.Envelope
import akka.kamon.instrumentation.ActorMonitors.{TracedMonitor, TrackedActor, TrackedRoutee}
import kamon.Kamon
import kamon.akka.Metrics
import kamon.akka.Metrics.{ActorGroupMetrics, ActorMetrics, RouterMetrics}
import kamon.trace.Span
import kamon.util.Clock
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
      val actorMetrics = if (cellInfo.isTracked) Some(Metrics.forActor(cellInfo.path, cellInfo.systemName, cellInfo.dispatcherName, cellInfo.actorClass.getName)) else None
      new TrackedActor(actorMetrics, trackingGroupMetrics(cellInfo), cellInfo.actorCellCreation, cellInfo)
    } else {
      ActorMonitors.ContextPropagationOnly(cellInfo)
    }
  }

  def createRouteeMonitor(cellInfo: CellInfo): ActorMonitor = {
    val routerMetrics = Metrics.forRouter(cellInfo.path, cellInfo.systemName, cellInfo.dispatcherName, cellInfo.actorClass.getName)
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
    private val actorClassName = cellInfo.actorClass.getName
    private val actorSimpleClassName = simpleClassName(cellInfo.actorClass)

    override def captureEnvelopeContext(): TimestampedContext = {
      monitor.captureEnvelopeContext()
    }

    override def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext, envelope: Envelope): AnyRef = {
      val messageSpan = buildSpan(cellInfo, envelopeContext, envelope)
      val contextWithMessageSpan = envelopeContext.context.withKey(Span.ContextKey, messageSpan)

      try {
        monitor.processMessage(pjp, envelopeContext.copy(context = contextWithMessageSpan), envelope)
      } finally {
        messageSpan.finish()
      }
    }

    override def processFailure(failure: Throwable): Unit = monitor.processFailure(failure)

    override def cleanup(): Unit = monitor.cleanup()

    private def buildSpan(cellInfo: CellInfo, envelopeContext: TimestampedContext, envelope: Envelope): Span = {
      val messageClass = simpleClassName(envelope.message.getClass)
      val parentSpan = envelopeContext.context.get(Span.ContextKey)
      val operationName = actorSimpleClassName + ": " + messageClass

      Kamon.buildSpan(operationName)
        .withStartTimestamp(Clock.toMicroTimestamp(envelopeContext.nanoTime))
        .asChildOf(parentSpan)
        .start()
        .mark("akka.actor.dequeued")
        .tag("component", "akka.actor")
        .tag("akka.system", cellInfo.systemName)
        .tag("akka.actor.path", cellInfo.path)
        .tag("akka.actor.class", actorClassName)
        .tag("akka.actor.message-class", messageClass)
    }
  }


  def ContextPropagationOnly(cellInfo: CellInfo) = new ActorMonitor {
    private val processedMessagesCounter = Metrics.forSystem(cellInfo.systemName).processedMessagesByNonTracked

    def captureEnvelopeContext(): TimestampedContext = TimestampedContext(0, Kamon.currentContext())

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext, envelope: Envelope): AnyRef = {
      processedMessagesCounter.increment()

      Kamon.withContext(envelopeContext.context) {
        pjp.proceed()
      }
    }

    def processFailure(failure: Throwable): Unit = {}
    def cleanup(): Unit = {
      Metrics.forSystem(cellInfo.systemName).activeActors.decrement()
    }
  }

  def simpleClassName(cls: Class[_]): String = {
    // could fail, check SI-2034
    try { cls.getSimpleName } catch { case _: Throwable => cls.getName }
  }

  class TrackedActor(actorMetrics: Option[ActorMetrics], groupMetrics: Seq[ActorGroupMetrics], actorCellCreation: Boolean, cellInfo: CellInfo)
      extends GroupMetricsTrackingActor(groupMetrics, actorCellCreation, cellInfo) {

    private val processedMessagesCounter = Metrics.forSystem(cellInfo.systemName).processedMessagesByTracked

    override def captureEnvelopeContext(): TimestampedContext = {
      actorMetrics.foreach { am =>
        am.mailboxSize.increment()
      }
      super.captureEnvelopeContext()
    }

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext, envelope: Envelope): AnyRef = {
      val timestampBeforeProcessing = System.nanoTime()
      processedMessagesCounter.increment()

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

    private val processedMessagesCounter = Metrics.forSystem(cellInfo.systemName).processedMessagesByTracked

    def processMessage(pjp: ProceedingJoinPoint, envelopeContext: TimestampedContext, envelope: Envelope): AnyRef = {
      val timestampBeforeProcessing = System.nanoTime()
      processedMessagesCounter.increment()

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
