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
package kamon.trace.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import akka.actor.{ Props, ActorSystem, ActorRef }
import akka.dispatch.{ Envelope, MessageDispatcher }
import com.codahale.metrics.Timer
import kamon.trace.{ ContextAware, TraceContext, Trace }

@Aspect
class BehaviourInvokeTracing {

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && args(system, ref, props, dispatcher, parent)")
  def actorCellCreation(system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {}

  @Pointcut("(execution(* akka.actor.ActorCell.invoke(*)) || execution(* akka.routing.RoutedActorCell.sendMessage(*))) && args(envelope)")
  def invokingActorBehaviourAtActorCell(envelope: Envelope) = {}

  @Around("invokingActorBehaviourAtActorCell(envelope)")
  def aroundBehaviourInvoke(pjp: ProceedingJoinPoint, envelope: Envelope): Unit = {
    //safe cast
    val ctxInMessage = envelope.asInstanceOf[ContextAware].traceContext

    Trace.withContext(ctxInMessage) {
      pjp.proceed()
    }
  }
}

@Aspect
class EnvelopeTraceContextMixin {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixin: ContextAware = ContextAware.default

  @Pointcut("execution(akka.dispatch.Envelope.new(..)) && this(ctx)")
  def envelopeCreation(ctx: ContextAware): Unit = {}

  @After("envelopeCreation(ctx)")
  def afterEnvelopeCreation(ctx: ContextAware): Unit = {
    // Necessary to force the initialization of ContextAware at the moment of creation.
    ctx.traceContext
  }
}
