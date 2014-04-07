/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */
package akka.instrumentation

import org.aspectj.lang.annotation._
import java.util.concurrent.atomic.AtomicInteger
import org.aspectj.lang.ProceedingJoinPoint

/**
 * This aspect adds a counter to the message queue to get O(1) time in the .numberOfMessages method implementation
 */
@Aspect
class UnboundedMailboxInstrumentation {

  @DeclareMixin("akka.dispatch.UnboundedMailbox$MessageQueue")
  def mixinMailboxToMailboxSizeAware: MessageQueueSizeCounting = new MessageQueueSizeCounting {}

  @Pointcut("execution(* akka.dispatch.UnboundedMailbox$MessageQueue.enqueue(..)) && this(queue)")
  def enqueuePointcut(queue: MessageQueueSizeCounting): Unit = {}

  @Pointcut("execution(* akka.dispatch.UnboundedMailbox$MessageQueue.dequeue()) && this(queue)")
  def dequeuePointcut(queue: MessageQueueSizeCounting): Unit = {}

  @Pointcut("execution(* akka.dispatch.UnboundedMailbox$MessageQueue.numberOfMessages()) && this(queue)")
  def numberOfMessagesPointcut(queue: MessageQueueSizeCounting): Unit = {}

  @After("dequeuePointcut(queue)")
  def afterDequeuePointcut(queue: MessageQueueSizeCounting): Unit = {
    if (queue.internalQueueSize.get() > 0) queue.internalQueueSize.decrementAndGet()
  }

  @After("enqueuePointcut(queue)")
  def afterEnqueue(queue: MessageQueueSizeCounting): Unit = {
    queue.internalQueueSize.incrementAndGet()
  }

  @Around("numberOfMessagesPointcut(queue)")
  def aroundNumberOfMessages(pjp: ProceedingJoinPoint, queue: MessageQueueSizeCounting): Any = {
    queue.internalQueueSize.get()
  }
}

trait MessageQueueSizeCounting {
  val internalQueueSize = new AtomicInteger
}
