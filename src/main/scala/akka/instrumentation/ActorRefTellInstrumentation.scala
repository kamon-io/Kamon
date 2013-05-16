package akka.instrumentation

import org.aspectj.lang.annotation.{Around, Pointcut, Aspect}
import org.aspectj.lang.ProceedingJoinPoint
import akka.actor.{ActorRef, ActorCell}
import kamon.TraceContext
import kamon.actor.TraceableMessage
import akka.dispatch.Envelope

@Aspect
class ActorRefTellInstrumentation {
  println("Created ActorAspect")

  @Pointcut("execution(* akka.actor.ScalaActorRef+.$bang(..)) && !within(akka.pattern.PromiseActorRef) && args(message, sender)")
  def sendingMessageToActorRef(message: Any, sender: ActorRef) = {}

  @Around("sendingMessageToActorRef(message, sender)")
  def around(pjp: ProceedingJoinPoint, message: Any, sender: ActorRef): Unit  = {
    import pjp._

    TraceContext.current match {
      case Some(ctx) => {
        val traceableMessage = TraceableMessage(ctx.fork, message)
        proceed(getArgs.updated(0, traceableMessage))
      }
      case None => proceed
    }
  }
}

@Aspect
class ActorCellInvokeInstrumentation {

  @Pointcut("execution(* akka.actor.ActorCell.invoke(*)) && args(envelope)")
  def invokingActorBehaviourAtActorCell(envelope: Envelope) = {}


  @Around("invokingActorBehaviourAtActorCell(envelope)")
  def around(pjp: ProceedingJoinPoint, envelope: Envelope) = {
    import pjp._

    envelope match {
      case Envelope(TraceableMessage(ctx, msg), sender) => {
        TraceContext.set(ctx)

        val originalEnvelope = envelope.copy(message = msg)
        proceed(getArgs.updated(0, originalEnvelope))

        TraceContext.clear
      }
      case _ => proceed
    }
  }
}