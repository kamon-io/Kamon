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
import kamon.akka.{ RouterMetrics, ActorMetrics }
import kamon.metric.Entity
import kamon.trace._
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class ActorCellInstrumentation {

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && this(cell) && args(system, ref, props, dispatcher, parent)")
  def actorCellCreation(cell: ActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {}

  @After("actorCellCreation(cell, system, ref, props, dispatcher, parent)")
  def afterCreation(cell: ActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {
    def isRootSupervisor(path: String): Boolean = path.length == 0 || path == "user" || path == "system"

    val pathString = ref.path.elements.mkString("/")
    val actorEntity = Entity(system.name + "/" + pathString, ActorMetrics.category)

    if (!isRootSupervisor(pathString) && Kamon.metrics.shouldTrack(actorEntity)) {
      val actorMetricsRecorder = Kamon.metrics.entity(ActorMetrics, actorEntity)
      val cellMetrics = cell.asInstanceOf[ActorCellMetrics]

      cellMetrics.entity = actorEntity
      cellMetrics.recorder = Some(actorMetricsRecorder)
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.invoke(*)) && this(cell) && args(envelope)")
  def invokingActorBehaviourAtActorCell(cell: ActorCell, envelope: Envelope) = {}

  @Around("invokingActorBehaviourAtActorCell(cell, envelope)")
  def aroundBehaviourInvoke(pjp: ProceedingJoinPoint, cell: ActorCell, envelope: Envelope): Any = {
    val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
    val timestampBeforeProcessing = System.nanoTime()
    val contextAndTimestamp = envelope.asInstanceOf[TimestampedTraceContextAware]

    try {
      Tracer.withContext(contextAndTimestamp.traceContext) {
        pjp.proceed()
      }
    } finally {
      val processingTime = System.nanoTime() - timestampBeforeProcessing
      val timeInMailbox = timestampBeforeProcessing - contextAndTimestamp.captureNanoTime

      cellMetrics.recorder.map { am ⇒
        am.processingTime.record(processingTime)
        am.timeInMailbox.record(timeInMailbox)
        am.mailboxSize.decrement()
      }

      // In case that this actor is behind a router, record the metrics for the router.
      envelope.asInstanceOf[RouterAwareEnvelope].routerMetricsRecorder.map { rm ⇒
        rm.processingTime.record(processingTime)
        rm.timeInMailbox.record(timeInMailbox)
      }
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.sendMessage(*)) && this(cell) && args(envelope)")
  def sendMessageInActorCell(cell: ActorCell, envelope: Envelope): Unit = {}

  @After("sendMessageInActorCell(cell, envelope)")
  def afterSendMessageInActorCell(cell: ActorCell, envelope: Envelope): Unit = {
    val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
    cellMetrics.recorder.map(_.mailboxSize.increment())
  }

  @Pointcut("execution(* akka.actor.ActorCell.stop()) && this(cell)")
  def actorStop(cell: ActorCell): Unit = {}

  @After("actorStop(cell)")
  def afterStop(cell: ActorCell): Unit = {
    val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
    cellMetrics.recorder.map { _ ⇒
      Kamon.metrics.removeEntity(cellMetrics.entity)
    }

    // The Stop can't be captured from the RoutedActorCell so we need to put this piece of cleanup here.
    if (cell.isInstanceOf[RoutedActorCell]) {
      val routedCellMetrics = cell.asInstanceOf[RoutedActorCellMetrics]
      routedCellMetrics.routerRecorder.map { _ ⇒
        Kamon.metrics.removeEntity(routedCellMetrics.routerEntity)
      }
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.handleInvokeFailure(..)) && this(cell)")
  def actorInvokeFailure(cell: ActorCell): Unit = {}

  @Before("actorInvokeFailure(cell)")
  def beforeInvokeFailure(cell: ActorCell): Unit = {
    val cellWithMetrics = cell.asInstanceOf[ActorCellMetrics]
    cellWithMetrics.recorder.map(_.errors.increment())

    // In case that this actor is behind a router, count the errors for the router as well.
    val envelope = cell.currentMessage.asInstanceOf[RouterAwareEnvelope]
    if (envelope ne null) {
      // The ActorCell.handleInvokeFailure(..) method is also called when a failure occurs
      // while processing a system message, in which case ActorCell.currentMessage is always
      // null.
      envelope.routerMetricsRecorder.map(_.errors.increment())
    }
  }
}

@Aspect
class RoutedActorCellInstrumentation {

  @Pointcut("execution(akka.routing.RoutedActorCell.new(..)) && this(cell) && args(system, ref, props, dispatcher, routeeProps, supervisor)")
  def routedActorCellCreation(cell: RoutedActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, routeeProps: Props, supervisor: ActorRef): Unit = {}

  @After("routedActorCellCreation(cell, system, ref, props, dispatcher, routeeProps, supervisor)")
  def afterRoutedActorCellCreation(cell: RoutedActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, routeeProps: Props, supervisor: ActorRef): Unit = {
    val routerEntity = Entity(system.name + "/" + ref.path.elements.mkString("/"), RouterMetrics.category)

    if (Kamon.metrics.shouldTrack(routerEntity)) {
      val cellMetrics = cell.asInstanceOf[RoutedActorCellMetrics]

      cellMetrics.routerEntity = routerEntity
      cellMetrics.routerRecorder = Some(Kamon.metrics.entity(RouterMetrics, routerEntity))
    }
  }

  @Pointcut("execution(* akka.routing.RoutedActorCell.sendMessage(*)) && this(cell) && args(envelope)")
  def sendMessageInRouterActorCell(cell: RoutedActorCell, envelope: Envelope) = {}

  @Around("sendMessageInRouterActorCell(cell, envelope)")
  def aroundSendMessageInRouterActorCell(pjp: ProceedingJoinPoint, cell: RoutedActorCell, envelope: Envelope): Any = {
    val cellMetrics = cell.asInstanceOf[RoutedActorCellMetrics]
    val timestampBeforeProcessing = System.nanoTime()
    val contextAndTimestamp = envelope.asInstanceOf[TimestampedTraceContextAware]

    try {
      Tracer.withContext(contextAndTimestamp.traceContext) {

        // The router metrics recorder will only be picked up if the message is sent from a tracked router.
        RouterAwareEnvelope.dynamicRouterMetricsRecorder.withValue(cellMetrics.routerRecorder) {
          pjp.proceed()
        }
      }
    } finally {
      cellMetrics.routerRecorder map { routerRecorder ⇒
        routerRecorder.routingTime.record(System.nanoTime() - timestampBeforeProcessing)
      }
    }
  }
}

trait ActorCellMetrics {
  var entity: Entity = _
  var recorder: Option[ActorMetrics] = None
}

trait RoutedActorCellMetrics {
  var routerEntity: Entity = _
  var routerRecorder: Option[RouterMetrics] = None
}

trait RouterAwareEnvelope {
  def routerMetricsRecorder: Option[RouterMetrics]
}

object RouterAwareEnvelope {
  import scala.util.DynamicVariable
  private[kamon] val dynamicRouterMetricsRecorder = new DynamicVariable[Option[RouterMetrics]](None)

  def default: RouterAwareEnvelope = new RouterAwareEnvelope {
    val routerMetricsRecorder: Option[RouterMetrics] = dynamicRouterMetricsRecorder.value
  }
}

@Aspect
class MetricsIntoActorCellsMixin {

  @DeclareMixin("akka.actor.ActorCell")
  def mixinActorCellMetricsToActorCell: ActorCellMetrics = new ActorCellMetrics {}

  @DeclareMixin("akka.routing.RoutedActorCell")
  def mixinActorCellMetricsToRoutedActorCell: RoutedActorCellMetrics = new RoutedActorCellMetrics {}

}

@Aspect
class TraceContextIntoEnvelopeMixin {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixinTraceContextAwareToEnvelope: TimestampedTraceContextAware = TimestampedTraceContextAware.default

  @DeclareMixin("akka.dispatch.Envelope")
  def mixinRouterAwareToEnvelope: RouterAwareEnvelope = RouterAwareEnvelope.default

  @Pointcut("execution(akka.dispatch.Envelope.new(..)) && this(ctx)")
  def envelopeCreation(ctx: TimestampedTraceContextAware): Unit = {}

  @After("envelopeCreation(ctx)")
  def afterEnvelopeCreation(ctx: TimestampedTraceContextAware with RouterAwareEnvelope): Unit = {
    // Necessary to force the initialization of ContextAware at the moment of creation.
    ctx.traceContext
    ctx.routerMetricsRecorder
  }
}