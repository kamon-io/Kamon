/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.instrumentation

import akka.dispatch.{ UnboundedMessageQueueSemantics, Envelope, MessageQueue }
import org.aspectj.lang.annotation.{ Around, Pointcut, DeclareMixin, Aspect }
import akka.actor.{ ActorSystem, ActorRef }
import org.aspectj.lang.ProceedingJoinPoint

/**
 *  For Mailboxes we would like to track the queue size and message latency. Currently the latency
 *  will be gathered from the ActorCellMetrics.
 */
/*

@Aspect
class MessageQueueInstrumentation {

  @Pointcut("execution(* akka.dispatch.MailboxType+.create(..)) && args(owner, system)")
  def messageQueueCreation(owner: Option[ActorRef], system: Option[ActorSystem]) = {}

  @Around("messageQueueCreation(owner, system)")
  def wrapMessageQueue(pjp: ProceedingJoinPoint, owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue = {
    val delegate = pjp.proceed.asInstanceOf[MessageQueue]

    // We are not interested in monitoring mailboxes if we don't know where they belong to.
    val monitoredMailbox = for (own ← owner; sys ← system) yield {
      val systemName = sys.name
      val ownerName = MetricDirectory.nameForActor(own)
      val mailBoxName = MetricDirectory.nameForMailbox(systemName, ownerName)

      val queueSizeHistogram = new Histogram(new ExponentiallyDecayingReservoir())
      Metrics.include(mailBoxName, queueSizeHistogram)

      new MonitoredMessageQueue(delegate, queueSizeHistogram)
    }

    monitoredMailbox match {
      case None      ⇒ delegate
      case Some(mmb) ⇒ mmb
    }
  }
}

class MonitoredMessageQueue(val delegate: MessageQueue, val queueSizeHistogram: Histogram) extends MessageQueue with UnboundedMessageQueueSemantics {

  def enqueue(receiver: ActorRef, handle: Envelope) = {
    delegate.enqueue(receiver, handle)
    //queueSizeHistogram.update(numberOfMessages)
  }

  def dequeue(): Envelope = {
    val envelope = delegate.dequeue()
    //queueSizeHistogram.update(numberOfMessages)

    envelope
  }

  def numberOfMessages: Int = delegate.numberOfMessages
  def hasMessages: Boolean = delegate.hasMessages
  def cleanUp(owner: ActorRef, deadLetters: MessageQueue) = delegate.cleanUp(owner, deadLetters)
}
*/

