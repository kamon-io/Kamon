package kamon.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import akka.actor.{ActorCell, Props, ActorSystem, ActorRef}
import kamon.{Kamon, Tracer, TraceContext}
import akka.dispatch.{MessageDispatcher, Envelope}
import com.codahale.metrics.Timer
import kamon.metric.{MetricDirectory, Metrics}
import scala.Some
import kamon.instrumentation.SimpleContextPassingInstrumentation.SimpleTraceMessage
import org.slf4j.MDC

case class TraceableMessage(traceContext: Option[TraceContext], message: Any, timer: Timer.Context)


@Aspect
class ActorRefTellInstrumentation {
  import ProceedingJoinPointPimp._

  @Pointcut("execution(* akka.actor.ScalaActorRef+.$bang(..)) && !within(akka.event.Logging.StandardOutLogger) && !within(akka.pattern.PromiseActorRef) && !within(akka.actor.DeadLetterActorRef) && target(actor) && args(message, sender)")
  def sendingMessageToActorRef(actor: ActorRef, message: Any, sender: ActorRef) = {}

  @Around("sendingMessageToActorRef(actor, message, sender)")
  def around(pjp: ProceedingJoinPoint, actor: ActorRef, message: Any, sender: ActorRef): Unit  = {
    import kamon.Instrument.instrumentation.sendMessageTransformation
    //println(s"====> [$sender] => [$actor] --- $message")
    pjp.proceedWithTarget(actor, sendMessageTransformation(sender, actor, message).asInstanceOf[AnyRef], sender)
  }
}



@Aspect("""perthis(actorCellCreation(akka.actor.ActorSystem, akka.actor.ActorRef, akka.actor.Props, akka.dispatch.MessageDispatcher, akka.actor.ActorRef))""")
class ActorCellInvokeInstrumentation {
  var instrumentation =  ActorReceiveInvokeInstrumentation.noopPreReceive
  var self: ActorRef = _

  // AKKA 2.2 introduces the dispatcher parameter. Maybe we could provide a dual pointcut.
  @Pointcut("execution(akka.actor.ActorCell.new(..)) && args(system, ref, props, dispatcher, parent)")
  def actorCellCreation(system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {}

  @After("actorCellCreation(system, ref, props, dispatcher, parent)")
  def registerMetricsInRegistry(system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {
    instrumentation = kamon.Instrument.instrumentation.receiveInvokeInstrumentation(system, ref, props, dispatcher, parent)
    self = ref
  }


  @Pointcut("(execution(* akka.actor.ActorCell.invoke(*)) || execution(* akka.routing.RoutedActorCell.sendMessage(*))) && args(envelope)")
  def invokingActorBehaviourAtActorCell(envelope: Envelope) = {}

  @Around("invokingActorBehaviourAtActorCell(envelope)")
  def around(pjp: ProceedingJoinPoint, envelope: Envelope): Unit = {
    import ProceedingJoinPointPimp._

    val (originalEnvelope, ctx) = instrumentation.preReceive(envelope)
    //println(s"====>[$ctx] ## [${originalEnvelope.sender}] => [$self] --- ${originalEnvelope.message}")
    ctx match {
      case Some(c) => {
        //MDC.put("uow", c.userContext.get.asInstanceOf[String])
        Tracer.set(c)
        pjp.proceedWith(originalEnvelope)
        Tracer.clear
        //MDC.remove("uow")
      }
      case None => pjp.proceedWith(originalEnvelope)
    }
  }
}


@Aspect
class UnregisteredActorRefInstrumentation {
  @Pointcut("execution(* akka.spray.UnregisteredActorRefBase+.handle(..)) && args(message, sender)")
  def sprayResponderHandle(message: Any, sender: ActorRef) = {}

  @Around("sprayResponderHandle(message, sender)")
  def sprayInvokeAround(pjp: ProceedingJoinPoint, message: Any, sender: ActorRef): Unit = {
    import ProceedingJoinPointPimp._
    //println("Handling unregistered actor ref message: "+message)
    message match {
      case SimpleTraceMessage(msg, ctx) => {
        ctx match {
          case Some(c) => {
            Tracer.set(c)
            pjp.proceedWith(msg.asInstanceOf[AnyRef])  // TODO: define if we should use Any or AnyRef and unify with the rest of the instrumentation.
            Tracer.clear
          }
          case None => pjp.proceedWith(msg.asInstanceOf[AnyRef])
        }
      }
      case _ => pjp.proceed
    }
  }
}


