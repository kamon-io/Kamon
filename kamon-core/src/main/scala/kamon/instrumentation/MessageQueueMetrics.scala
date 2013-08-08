package kamon.instrumentation

import com.codahale.metrics.{ExponentiallyDecayingReservoir, Histogram}
import akka.dispatch.{UnboundedMessageQueueSemantics, Envelope, MessageQueue}
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
    val monitoredMailbox = for(own <- owner; sys <- system) yield {
      val systemName = sys.name
      val ownerName = MetricDirectory.nameForActor(own)
      val mailBoxName = MetricDirectory.nameForMailbox(systemName, ownerName)

      val queueSizeHistogram = new Histogram(new ExponentiallyDecayingReservoir())
      Metrics.include(mailBoxName, queueSizeHistogram)

      new MonitoredMessageQueue(delegate, queueSizeHistogram)
    }

    monitoredMailbox match {
      case None => delegate
      case Some(mmb) => mmb
    }
  }
}


class MonitoredMessageQueue(val delegate: MessageQueue, val queueSizeHistogram: Histogram) extends MessageQueue with UnboundedMessageQueueSemantics{

  def enqueue(receiver: ActorRef, handle: Envelope) = {
    delegate.enqueue(receiver, handle)
    queueSizeHistogram.update(numberOfMessages)
  }

  def dequeue(): Envelope = {
    val envelope = delegate.dequeue()
    queueSizeHistogram.update(numberOfMessages)

    envelope
  }

  def numberOfMessages: Int = delegate.numberOfMessages
  def hasMessages: Boolean = delegate.hasMessages
  def cleanUp(owner: ActorRef, deadLetters: MessageQueue) = delegate.cleanUp(owner, deadLetters)
}









