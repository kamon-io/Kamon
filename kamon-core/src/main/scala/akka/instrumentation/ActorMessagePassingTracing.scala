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
package akka.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import akka.actor.{ Cell, Props, ActorSystem, ActorRef }
import akka.dispatch.{ Envelope, MessageDispatcher }
import kamon.trace.{ TraceContext, ContextAware, Trace }
import kamon.metrics.{ HdrActorMetricsRecorder, ActorMetrics }
import kamon.Kamon

@Aspect("perthis(actorCellCreation(*, *, *, *, *))")
class BehaviourInvokeTracing {
  var path: Option[String] = None
  var actorMetrics: Option[HdrActorMetricsRecorder] = None

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && args(system, ref, props, dispatcher, parent)")
  def actorCellCreation(system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {}

  @After("actorCellCreation(system, ref, props, dispatcher, parent)")
  def afterCreation(system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {
    val metricsExtension = Kamon(ActorMetrics)(system)
    val simplePathString = ref.path.elements.mkString("/")

    if (metricsExtension.shouldTrackActor(simplePathString)) {
      path = Some(ref.path.toString)
      actorMetrics = Some(metricsExtension.registerActor(simplePathString))
    }
  }

  @Pointcut("(execution(* akka.actor.ActorCell.invoke(*)) || execution(* akka.routing.RoutedActorCell.sendMessage(*))) && args(envelope)")
  def invokingActorBehaviourAtActorCell(envelope: Envelope) = {}

  @Around("invokingActorBehaviourAtActorCell(envelope)")
  def aroundBehaviourInvoke(pjp: ProceedingJoinPoint, envelope: Envelope): Unit = {
    val timestampBeforeProcessing = System.nanoTime()
    val contextAndTimestamp = envelope.asInstanceOf[ContextAndTimestampAware]

    Trace.withContext(contextAndTimestamp.traceContext) {
      pjp.proceed()
    }

    actorMetrics.map { am ⇒
      am.recordProcessingTime(System.nanoTime() - timestampBeforeProcessing)
      am.recordTimeInMailbox(timestampBeforeProcessing - contextAndTimestamp.timestamp)
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.stop()) && this(cell)")
  def actorStop(cell: Cell): Unit = {}

  @After("actorStop(cell)")
  def afterStop(cell: Cell): Unit = {
    path.map(p ⇒ Kamon(ActorMetrics)(cell.system).unregisterActor(p))
  }
}

@Aspect
class EnvelopeTraceContextMixin {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixin: ContextAndTimestampAware = new ContextAndTimestampAware {
    val traceContext: Option[TraceContext] = Trace.context()
    val timestamp: Long = System.nanoTime()
  }

  @Pointcut("execution(akka.dispatch.Envelope.new(..)) && this(ctx)")
  def envelopeCreation(ctx: ContextAware): Unit = {}

  @After("envelopeCreation(ctx)")
  def afterEnvelopeCreation(ctx: ContextAware): Unit = {
    // Necessary to force the initialization of ContextAware at the moment of creation.
    ctx.traceContext
  }
}

trait ContextAndTimestampAware extends ContextAware {
  def timestamp: Long
}
