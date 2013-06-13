package akka.instrumentation

import com.codahale.metrics.{ExponentiallyDecayingReservoir, Histogram}
import akka.dispatch.{Envelope, MessageQueue}
import org.aspectj.lang.annotation.{Around, Pointcut, DeclareMixin, Aspect}
import akka.actor.{ActorSystem, ActorRef}
import kamon.metric.{Metrics, MetricDirectory}
import org.aspectj.lang.ProceedingJoinPoint


/**
 *  For Mailboxes we would like to track the queue size and message latency. Currently the latency
 *  will be gathered from the ActorCellMetrics.
 */


@Aspect
class MessageQueueInstrumentation {

  @Pointcut("execution(* akka.dispatch.MailboxType+.create(..)) && args(owner, system)")
  def messageQueueCreation(owner: Option[ActorRef], system: Option[ActorSystem]) = {}

  @Around("messageQueueCreation(owner, system)")
  def wrapMessageQueue(pjp: ProceedingJoinPoint, owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = {
    val delegate = pjp.proceed.asInstanceOf[MessageQueue]

    // We are not interested in monitoring mailboxes if we don't know where they belong to.
    val monitoredMailbox = for(own <- owner; sys <- system) yield new MonitoredMessageQueue(delegate, own, sys)

    monitoredMailbox match {
      case None => delegate
      case Some(mmb) => mmb
    }

  }
}


class MonitoredMessageQueue(val delegate: MessageQueue, owner: ActorRef, system: ActorSystem) extends MessageQueue {
  val queueSizeHistogram: Histogram = new Histogram(new ExponentiallyDecayingReservoir)

  val fullName = MetricDirectory.nameForMailbox(system.name, MetricDirectory.nameForActor(owner))
  Metrics.registry.register(fullName, queueSizeHistogram)

  def enqueue(receiver: ActorRef, handle: Envelope) = {
    queueSizeHistogram.update(numberOfMessages)
    delegate.enqueue(receiver, handle)
  }

  def dequeue(): Envelope = {
    queueSizeHistogram.update(numberOfMessages)
    delegate.dequeue()
  }

  def numberOfMessages: Int = delegate.numberOfMessages
  def hasMessages: Boolean = delegate.hasMessages
  def cleanUp(owner: ActorRef, deadLetters: MessageQueue) = {
    Metrics.deregister(fullName)

    delegate.cleanUp(owner, deadLetters)
  }
}









