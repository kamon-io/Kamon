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

import akka.actor._
import akka.dispatch.{ Envelope, MessageDispatcher }
import akka.routing.RoutedActorCell
import kamon.Kamon
import kamon.metric.ActorMetrics.ActorMetricsRecorder
import kamon.metric.RouterMetrics.RouterMetricsRecorder
import kamon.metric.{ ActorMetrics, Metrics, RouterMetrics }
import kamon.trace._
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class ActorCellInstrumentation {

  import ActorCellInstrumentation.PimpedActorCellMetrics

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && this(cell) && args(system, ref, props, dispatcher, parent)")
  def actorCellCreation(cell: ActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {}

  @After("actorCellCreation(cell, system, ref, props, dispatcher, parent)")
  def afterCreation(cell: ActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {

    val metricsExtension = Kamon(Metrics)(system)
    val metricIdentity = ActorMetrics(ref.path.elements.mkString("/"))
    val cellWithMetrics = cell.asInstanceOf[ActorCellMetrics]

    cellWithMetrics.actorMetricIdentity = metricIdentity
    cellWithMetrics.actorMetricsRecorder = metricsExtension.register(metricIdentity, ActorMetrics.Factory)

    cellWithMetrics.onRoutedActorCell { routedActorCell ⇒
      val routerMetricIdentity = RouterMetrics(s"${routedActorCell.asInstanceOf[RoutedActorCell].self.path.elements.mkString("/")}")
      routedActorCell.routerMetricIdentity = routerMetricIdentity
      routedActorCell.routerMetricsRecorder = metricsExtension.register(routerMetricIdentity, RouterMetrics.Factory)
    }
  }

  @Pointcut("(execution(* akka.actor.ActorCell.invoke(*)) || execution(* akka.routing.RoutedActorCell.sendMessage(*))) && this(cell) && args(envelope)")
  def invokingActorBehaviourAtActorCell(cell: ActorCell, envelope: Envelope) = {}

  @Around("invokingActorBehaviourAtActorCell(cell, envelope)")
  def aroundBehaviourInvoke(pjp: ProceedingJoinPoint, cell: ActorCell, envelope: Envelope): Any = {
    val cellWithMetrics = cell.asInstanceOf[ActorCellMetrics]
    val timestampBeforeProcessing = System.nanoTime()
    val contextAndTimestamp = envelope.asInstanceOf[TimestampedTraceContextAware]

    try {
      TraceRecorder.withInlineTraceContextReplacement(contextAndTimestamp.traceContext) {
        pjp.proceed()
      }
    } finally {
      cellWithMetrics.actorMetricsRecorder.map {
        am ⇒
          val processingTime = System.nanoTime() - timestampBeforeProcessing
          val timeInMailbox = timestampBeforeProcessing - contextAndTimestamp.captureNanoTime

          am.processingTime.record(processingTime)
          am.timeInMailbox.record(timeInMailbox)
          am.mailboxSize.decrement()

          (processingTime, timeInMailbox)
      } map {
        case (processingTime, timeInMailbox) ⇒
          cellWithMetrics.onRoutedActorCell { routedActorCell ⇒
            routedActorCell.routerMetricsRecorder.map {
              rm ⇒
                rm.processingTime.record(processingTime)
                rm.timeInMailbox.record(timeInMailbox)
            }
          }
      }
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.sendMessage(*)) && this(cell)")
  def sendingMessageToActorCell(cell: ActorCell): Unit = {}

  @After("sendingMessageToActorCell(cell)")
  def afterSendMessageToActorCell(cell: ActorCell): Unit = {
    val cellWithMetrics = cell.asInstanceOf[ActorCellMetrics]
    cellWithMetrics.actorMetricsRecorder.map(am ⇒ am.mailboxSize.increment())
  }

  @Pointcut("execution(* akka.actor.ActorCell.stop()) && this(cell)")
  def actorStop(cell: ActorCell): Unit = {}

  @After("actorStop(cell)")
  def afterStop(cell: ActorCell): Unit = {
    val cellWithMetrics = cell.asInstanceOf[ActorCellMetrics]

    cellWithMetrics.actorMetricsRecorder.map { p ⇒
      Kamon(Metrics)(cell.system).unregister(cellWithMetrics.actorMetricIdentity)
    }

    cellWithMetrics.onRoutedActorCell { routedActorCell ⇒
      routedActorCell.routerMetricsRecorder.map { rm ⇒
        Kamon(Metrics)(cell.system).unregister(cellWithMetrics.routerMetricIdentity)
      }
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.handleInvokeFailure(..)) && this(cell)")
  def actorInvokeFailure(cell: ActorCell): Unit = {}

  @Before("actorInvokeFailure(cell)")
  def beforeInvokeFailure(cell: ActorCell): Unit = {
    val cellWithMetrics = cell.asInstanceOf[ActorCellMetrics]

    cellWithMetrics.actorMetricsRecorder.map {
      am ⇒ am.errors.increment()
    }

    cellWithMetrics.onRoutedActorCell { routedActorCell ⇒
      routedActorCell.routerMetricsRecorder.map {
        rm ⇒ rm.errors.increment()
      }
    }
  }

}

trait ActorCellMetrics {
  var actorMetricIdentity: ActorMetrics = _
  var routerMetricIdentity: RouterMetrics = _
  var actorMetricsRecorder: Option[ActorMetricsRecorder] = _
  var routerMetricsRecorder: Option[RouterMetricsRecorder] = _
}

@Aspect
class ActorCellMetricsIntoActorCellMixin {

  @DeclareMixin("akka.actor.ActorCell")
  def mixinActorCellMetricsToActorCell: ActorCellMetrics = new ActorCellMetrics {}
}

@Aspect
class TraceContextIntoEnvelopeMixin {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixinTraceContextAwareToEnvelope: TimestampedTraceContextAware = TimestampedTraceContextAware.default

  @Pointcut("execution(akka.dispatch.Envelope.new(..)) && this(ctx)")
  def envelopeCreation(ctx: TimestampedTraceContextAware): Unit = {}

  @After("envelopeCreation(ctx)")
  def afterEnvelopeCreation(ctx: TimestampedTraceContextAware): Unit = {
    // Necessary to force the initialization of ContextAware at the moment of creation.
    ctx.traceContext
  }
}

object ActorCellInstrumentation {
  implicit class PimpedActorCellMetrics(cell: ActorCellMetrics) {
    def onRoutedActorCell(block: ActorCellMetrics ⇒ Unit) = cell match {
      case routedActorCell: RoutedActorCell ⇒ block(cell)
      case everythingElse                   ⇒
    }
  }
}