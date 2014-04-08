/* ===================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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
import akka.actor._
import akka.dispatch.{Envelope, MessageDispatcher}
import kamon.trace._
import kamon.metrics.{ActorMetrics, Metrics}
import kamon.Kamon
import kamon.metrics.ActorMetrics.ActorMetricRecorder
import java.util.concurrent.atomic.AtomicInteger

@Aspect
class BehaviourInvokeTracing {

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && this(cell) && args(system, ref, props, dispatcher, parent)")
  def actorCellCreation(cell: ActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {}

  @After("actorCellCreation(cell, system, ref, props, dispatcher, parent)")
  def afterCreation(cell: ActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {
    val metricsExtension = Kamon(Metrics)(system)
    val metricIdentity = ActorMetrics(ref.path.elements.mkString("/"))
    val cellWithMetrics = cell.asInstanceOf[ActorCellMetrics]

    cellWithMetrics.metricIdentity = metricIdentity
    cellWithMetrics.actorMetricsRecorder = metricsExtension.register(metricIdentity, ActorMetrics.Factory)
  }

  @Pointcut("(execution(* akka.actor.ActorCell.invoke(*)) || execution(* akka.routing.RoutedActorCell.sendMessage(*))) && this(cell) && args(envelope)")
  def invokingActorBehaviourAtActorCell(cell: ActorCell, envelope: Envelope) = {}

  @Around("invokingActorBehaviourAtActorCell(cell, envelope)")
  def aroundBehaviourInvoke(pjp: ProceedingJoinPoint, cell: ActorCell, envelope: Envelope): Any = {
    val timestampBeforeProcessing = System.nanoTime()
    val contextAndTimestamp = envelope.asInstanceOf[TraceContextAware]
    val cellWithMetrics = cell.asInstanceOf[ActorCellMetrics]

    try {
      TraceRecorder.withTraceContext(contextAndTimestamp.traceContext) {
        pjp.proceed()
      }
    } finally {
      cellWithMetrics.actorMetricsRecorder.map {
        am ⇒
          am.processingTime.record(System.nanoTime() - timestampBeforeProcessing)
          am.timeInMailbox.record(timestampBeforeProcessing - contextAndTimestamp.captureNanoTime)
          am.mailboxSize.record(cellWithMetrics.queueSize.decrementAndGet())
      }
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.sendMessage(*)) && this(cell)")
  def sendingMessageToActorCell(cell: ActorCell): Unit = {}

  @After("sendingMessageToActorCell(cell)")
  def afterSendMessageToActorCell(cell: ActorCell): Unit = {
    val cellWithMetrics = cell.asInstanceOf[ActorCellMetrics]
    cellWithMetrics.actorMetricsRecorder.map {
      am ⇒
        am.mailboxSize.record(cellWithMetrics.queueSize.incrementAndGet())
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.stop()) && this(cell)")
  def actorStop(cell: ActorCell): Unit = {}

  @After("actorStop(cell)")
  def afterStop(cell: ActorCell): Unit = {
    val cellWithMetrics = cell.asInstanceOf[ActorCellMetrics]
    cellWithMetrics.actorMetricsRecorder.map(p ⇒ Kamon(Metrics)(cell.system).unregister(cellWithMetrics.metricIdentity))
  }
}

trait ActorCellMetrics {
  var metricIdentity: ActorMetrics = _
  var actorMetricsRecorder: Option[ActorMetricRecorder] = _
  val queueSize = new AtomicInteger
}

@Aspect
class ActorCellMetricsMixin {

  @DeclareMixin("akka.actor.ActorCell")
  def mixinActorCellMetricsToActorCell: ActorCellMetrics = new ActorCellMetrics {}
}

@Aspect
class EnvelopeTraceContextMixin {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixinTraceContextAwareToEnvelope: TraceContextAware = TraceContextAware.default

  @Pointcut("execution(akka.dispatch.Envelope.new(..)) && this(ctx)")
  def envelopeCreation(ctx: TraceContextAware): Unit = {}

  @After("envelopeCreation(ctx)")
  def afterEnvelopeCreation(ctx: TraceContextAware): Unit = {
    // Necessary to force the initialization of ContextAware at the moment of creation.
    ctx.traceContext
  }
}