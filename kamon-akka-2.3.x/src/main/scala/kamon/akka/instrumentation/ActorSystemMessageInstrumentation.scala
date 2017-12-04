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
import kamon.akka.context.HasTransientContext
import kamon.context.HasContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class ActorSystemMessageInstrumentation {

  @Pointcut("execution(* akka.actor.ActorCell.invokeAll$1(..)) && args(messages, *)")
  def systemMessageProcessing(messages: EarliestFirstSystemMessageList): Unit = {}

  @Around("systemMessageProcessing(messages)")
  def aroundSystemMessageInvoke(pjp: ProceedingJoinPoint, messages: EarliestFirstSystemMessageList): Any = {
    if (messages.nonEmpty) {
      val context = messages.head.asInstanceOf[HasContext].context
      Kamon.withContext(context)(pjp.proceed())

    } else pjp.proceed()
  }
}

@Aspect
class HasContextIntoSystemMessageMixin {

  @DeclareMixin("akka.dispatch.sysmsg.SystemMessage+")
  def mixinHasContextToSystemMessage: HasContext = HasTransientContext.fromCurrentContext()

  @Pointcut("execution(akka.dispatch.sysmsg.SystemMessage+.new(..)) && this(message)")
  def systemMessageCreation(message: HasContext): Unit = {}

  @After("systemMessageCreation(message)")
  def afterSystemMessageCreation(message: HasContext): Unit = {
    // Necessary to force the initialization of HasContext at the moment of creation.
    message.context
  }
}

@Aspect
class HasContextIntoRepointableActorRefMixin {

  @DeclareMixin("akka.actor.RepointableActorRef")
  def mixinHasContextToRepointableActorRef: HasContext = HasTransientContext.fromCurrentContext()

  @Pointcut("execution(akka.actor.RepointableActorRef.new(..)) && this(repointableActorRef)")
  def envelopeCreation(repointableActorRef: HasContext): Unit = {}

  @After("envelopeCreation(repointableActorRef)")
  def afterEnvelopeCreation(repointableActorRef: HasContext): Unit = {
    // Necessary to force the initialization of HasContext at the moment of creation.
    repointableActorRef.context
  }

  @Pointcut("execution(* akka.actor.RepointableActorRef.point(..)) && this(repointableActorRef)")
  def repointableActorRefCreation(repointableActorRef: HasContext): Unit = {}

  @Around("repointableActorRefCreation(repointableActorRef)")
  def afterRepointableActorRefCreation(pjp: ProceedingJoinPoint, repointableActorRef: HasContext): Any = {
    Kamon.withContext(repointableActorRef.context) {
      pjp.proceed()
    }
  }
}