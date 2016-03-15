package akka.kamon.instrumentation

import akka.actor.{ Cell, ActorRef, ActorSystem }
import akka.kamon.instrumentation.ActorMonitors.{ TrackedRoutee, TrackedActor }
import kamon.Kamon
import kamon.akka.{ RouterMetrics, ActorMetrics }
import kamon.metric.Entity
import kamon.trace.{ TraceContext, EmptyTraceContext, Tracer }
import kamon.util.RelativeNanoTimestamp
import org.aspectj.lang.ProceedingJoinPoint

trait ActorMonitor {
  def captureEnvelopeContext(): EnvelopeContext
  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef
  def processFailure(failure: Throwable): Unit
  def cleanup(): Unit
}

object ActorMonitor {

  def createActorMonitor(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef): ActorMonitor = {
    val cellInfo = CellInfo.cellInfoFor(cell, system, ref, parent)

    if (cellInfo.isRouter)
      ActorMonitors.NoOp
    else {
      if (cellInfo.isRoutee)
        createRouteeMonitor(cellInfo)
      else
        createRegularActorMonitor(cellInfo)
    }
  }

  def createRegularActorMonitor(cellInfo: CellInfo): ActorMonitor = {
    def actorMetrics = Kamon.metrics.entity(ActorMetrics, cellInfo.entity)

    if (cellInfo.isTracked)
      new TrackedActor(cellInfo.entity, actorMetrics)
    else ActorMonitors.ContextPropagationOnly
  }

  def createRouteeMonitor(cellInfo: CellInfo): ActorMonitor = {
    def routerMetrics = Kamon.metrics.entity(RouterMetrics, cellInfo.entity)

    if (cellInfo.isTracked)
      new TrackedRoutee(cellInfo.entity, routerMetrics)
    else ActorMonitors.ContextPropagationOnly
  }
}

object ActorMonitors {
  val NoOp = new ActorMonitor {
    override def captureEnvelopeContext(): EnvelopeContext = EnvelopeContext(RelativeNanoTimestamp.zero, EmptyTraceContext)
    override def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef = pjp.proceed()
    override def processFailure(failure: Throwable): Unit = {}
    override def cleanup(): Unit = {}
  }

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

  class TrackedActor(val entity: Entity, actorMetrics: ActorMetrics) extends ActorMonitor {
    def captureEnvelopeContext(): EnvelopeContext = {
      actorMetrics.mailboxSize.increment()
      EnvelopeContext(RelativeNanoTimestamp.now, Tracer.currentContext)
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

        actorMetrics.processingTime.record(processingTime.nanos)
        actorMetrics.timeInMailbox.record(timeInMailbox.nanos)
        actorMetrics.mailboxSize.decrement()
      }
    }

    def processFailure(failure: Throwable): Unit = actorMetrics.errors.increment()
    def cleanup(): Unit = Kamon.metrics.removeEntity(entity)
  }

  class TrackedRoutee(val entity: Entity, routerMetrics: RouterMetrics) extends ActorMonitor {
    def captureEnvelopeContext(): EnvelopeContext =
      EnvelopeContext(RelativeNanoTimestamp.now, Tracer.currentContext)

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
      }
    }

    def processFailure(failure: Throwable): Unit = routerMetrics.errors.increment()
    def cleanup(): Unit = {}
  }
}