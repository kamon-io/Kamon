package akka.kamon.instrumentation

import java.util.concurrent.locks.ReentrantLock

import akka.actor._
import akka.dispatch.Envelope
import akka.dispatch.sysmsg.SystemMessage
import akka.routing.RoutedActorCell
import kamon.trace.Tracer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

import scala.collection.immutable

@Aspect
class ActorCellInstrumentation {

  def actorInstrumentation(cell: Cell): ActorMonitor =
    cell.asInstanceOf[ActorInstrumentationAware].actorInstrumentation

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && this(cell) && args(system, ref, *, *, parent)")
  def actorCellCreation(cell: Cell, system: ActorSystem, ref: ActorRef, parent: InternalActorRef): Unit = {}

  @Pointcut("execution(akka.actor.UnstartedCell.new(..)) && this(cell) && args(system, ref, *, parent)")
  def repointableActorRefCreation(cell: Cell, system: ActorSystem, ref: ActorRef, parent: InternalActorRef): Unit = {}

  @After("actorCellCreation(cell, system, ref, parent)")
  def afterCreation(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef): Unit = {
    cell.asInstanceOf[ActorInstrumentationAware].setActorInstrumentation(
      ActorMonitor.createActorMonitor(cell, system, ref, parent, true))
  }

  @After("repointableActorRefCreation(cell, system, ref, parent)")
  def afterRepointableActorRefCreation(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef): Unit = {
    cell.asInstanceOf[ActorInstrumentationAware].setActorInstrumentation(
      ActorMonitor.createActorMonitor(cell, system, ref, parent, false))
  }

  @Pointcut("execution(* akka.actor.ActorCell.invoke(*)) && this(cell) && args(envelope)")
  def invokingActorBehaviourAtActorCell(cell: ActorCell, envelope: Envelope) = {}

  @Around("invokingActorBehaviourAtActorCell(cell, envelope)")
  def aroundBehaviourInvoke(pjp: ProceedingJoinPoint, cell: ActorCell, envelope: Envelope): Any = {
    actorInstrumentation(cell).processMessage(pjp, envelope.asInstanceOf[InstrumentedEnvelope].envelopeContext())
  }

  /**
   *
   *
   */

  @Pointcut("execution(* akka.actor.ActorCell.sendMessage(*)) && this(cell) && args(envelope)")
  def sendMessageInActorCell(cell: Cell, envelope: Envelope): Unit = {}

  @Pointcut("execution(* akka.actor.UnstartedCell.sendMessage(*)) && this(cell) && args(envelope)")
  def sendMessageInUnstartedActorCell(cell: Cell, envelope: Envelope): Unit = {}

  @Before("sendMessageInActorCell(cell, envelope)")
  def afterSendMessageInActorCell(cell: Cell, envelope: Envelope): Unit = {
    envelope.asInstanceOf[InstrumentedEnvelope].setEnvelopeContext(
      actorInstrumentation(cell).captureEnvelopeContext())
  }

  @Before("sendMessageInUnstartedActorCell(cell, envelope)")
  def afterSendMessageInUnstartedActorCell(cell: Cell, envelope: Envelope): Unit = {
    envelope.asInstanceOf[InstrumentedEnvelope].setEnvelopeContext(
      actorInstrumentation(cell).captureEnvelopeContext())
  }

  @Pointcut("execution(* akka.actor.UnstartedCell.replaceWith(*)) && this(unStartedCell) && args(cell)")
  def replaceWithInRepointableActorRef(unStartedCell: UnstartedCell, cell: Cell): Unit = {}

  @Around("replaceWithInRepointableActorRef(unStartedCell, cell)")
  def aroundReplaceWithInRepointableActorRef(pjp: ProceedingJoinPoint, unStartedCell: UnstartedCell, cell: Cell): Unit = {
    // TODO: Find a way to do this without resorting to reflection and, even better, without copy/pasting the Akka Code!
    val unstartedCellClass = classOf[UnstartedCell]
    val queueField = unstartedCellClass.getDeclaredField("akka$actor$UnstartedCell$$queue")
    queueField.setAccessible(true)

    val lockField = unstartedCellClass.getDeclaredField("lock")
    lockField.setAccessible(true)

    val queue = queueField.get(unStartedCell).asInstanceOf[java.util.LinkedList[_]]
    val lock = lockField.get(unStartedCell).asInstanceOf[ReentrantLock]

    def locked[T](body: ⇒ T): T = {
      lock.lock()
      try body finally lock.unlock()
    }

    locked {
      try {
        while (!queue.isEmpty) {
          queue.poll() match {
            case s: SystemMessage ⇒ cell.sendSystemMessage(s) // TODO: ============= CHECK SYSTEM MESSAGESSSSS =========
            case e: Envelope with InstrumentedEnvelope ⇒
              Tracer.withContext(e.envelopeContext().context) {
                cell.sendMessage(e)
              }
          }
        }
      } finally {
        unStartedCell.self.swapCell(cell)
      }
    }
  }

  /**
   *
   */

  @Pointcut("execution(* akka.actor.ActorCell.stop()) && this(cell)")
  def actorStop(cell: ActorCell): Unit = {}

  @After("actorStop(cell)")
  def afterStop(cell: ActorCell): Unit = {
    actorInstrumentation(cell).cleanup()

    // The Stop can't be captured from the RoutedActorCell so we need to put this piece of cleanup here.
    if (cell.isInstanceOf[RoutedActorCell]) {
      cell.asInstanceOf[RouterInstrumentationAware].routerInstrumentation.cleanup()
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.handleInvokeFailure(..)) && this(cell) && args(childrenNotToSuspend, failure)")
  def actorInvokeFailure(cell: ActorCell, childrenNotToSuspend: immutable.Iterable[ActorRef], failure: Throwable): Unit = {}

  @Before("actorInvokeFailure(cell, childrenNotToSuspend, failure)")
  def beforeInvokeFailure(cell: ActorCell, childrenNotToSuspend: immutable.Iterable[ActorRef], failure: Throwable): Unit = {
    actorInstrumentation(cell).processFailure(failure)
  }
}

trait ActorInstrumentationAware {
  def actorInstrumentation: ActorMonitor
  def setActorInstrumentation(ai: ActorMonitor): Unit
}

object ActorInstrumentationAware {
  def apply(): ActorInstrumentationAware = new ActorInstrumentationAware {
    private var _ai: ActorMonitor = _

    def setActorInstrumentation(ai: ActorMonitor): Unit = _ai = ai
    def actorInstrumentation: ActorMonitor = _ai
  }
}

@Aspect
class MetricsIntoActorCellsMixin {

  @DeclareMixin("akka.actor.ActorCell")
  def mixinActorCellMetricsToActorCell: ActorInstrumentationAware = ActorInstrumentationAware()

  @DeclareMixin("akka.actor.UnstartedCell")
  def mixinActorCellMetricsToUnstartedActorCell: ActorInstrumentationAware = ActorInstrumentationAware()

}
