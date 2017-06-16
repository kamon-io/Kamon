/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package akka.kamon.instrumentation

import akka.dispatch.sysmsg.EarliestFirstSystemMessageList
import kamon.Kamon
import kamon.util.HasContinuation
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class ActorSystemMessageInstrumentation {

  @Pointcut("execution(* akka.actor.ActorCell.invokeAll$1(..)) && args(messages, *)")
  def systemMessageProcessing(messages: EarliestFirstSystemMessageList): Unit = {}

  @Around("systemMessageProcessing(messages)")
  def aroundSystemMessageInvoke(pjp: ProceedingJoinPoint, messages: EarliestFirstSystemMessageList): Any = {
    if (messages.nonEmpty) {
      val continuation = messages.head.asInstanceOf[HasContinuation].continuation
      Kamon.withContinuation(continuation)(pjp.proceed())

    } else pjp.proceed()
  }
}

@Aspect
class TraceContextIntoSystemMessageMixin {

  @DeclareMixin("akka.dispatch.sysmsg.SystemMessage+")
  def mixinHasContinuationToSystemMessage: HasContinuation = HasContinuation.fromTracerActiveSpan()

  @Pointcut("execution(akka.dispatch.sysmsg.SystemMessage+.new(..)) && this(message)")
  def systemMessageCreation(message: HasContinuation): Unit = {}

  @After("systemMessageCreation(message)")
  def afterSystemMessageCreation(message: HasContinuation): Unit = {
    // Necessary to force the initialization of HasContinuation at the moment of creation.
    message.continuation
  }
}

@Aspect
class TraceContextIntoRepointableActorRefMixin {

  @DeclareMixin("akka.actor.RepointableActorRef")
  def mixinTraceContextAwareToRepointableActorRef: HasContinuation = HasContinuation.fromTracerActiveSpan()

  @Pointcut("execution(akka.actor.RepointableActorRef.new(..)) && this(repointableActorRef)")
  def envelopeCreation(repointableActorRef: HasContinuation): Unit = {}

  @After("envelopeCreation(repointableActorRef)")
  def afterEnvelopeCreation(repointableActorRef: HasContinuation): Unit = {
    // Necessary to force the initialization of HasContinuation at the moment of creation.
    repointableActorRef.continuation
  }

  @Pointcut("execution(* akka.actor.RepointableActorRef.point(..)) && this(repointableActorRef)")
  def repointableActorRefCreation(repointableActorRef: HasContinuation): Unit = {}

  @Around("repointableActorRefCreation(repointableActorRef)")
  def afterRepointableActorRefCreation(pjp: ProceedingJoinPoint, repointableActorRef: HasContinuation): Any = {
    Kamon.withContinuation(repointableActorRef.continuation) {
      pjp.proceed()
    }
  }
}