package kamon.instrumentation

import org.aspectj.lang.annotation._
import org.aspectj.lang.ProceedingJoinPoint
import akka.actor.{Props, ActorSystem, ActorRef}
import kamon.{Tracer, TraceContext}
import akka.dispatch.{MessageDispatcher, Envelope}
import com.codahale.metrics.{Timer, ExponentiallyDecayingReservoir, Histogram}
import kamon.metric.{MetricDirectory, Metrics}
import com.codahale.metrics
import scala.Some

case class TraceableMessage(traceContext: Option[TraceContext], message: Any, timer: Timer.Context)


@Aspect
class ActorRefTellInstrumentation {
  import ProceedingJoinPointPimp._

  @Pointcut("execution(* (akka.actor.ScalaActorRef+ && !akka.event.Logging$StandardOutLogger).$bang(..)) && target(actor) && args(message, sender)")
  def sendingMessageToActorRef(actor: ActorRef, message: Any, sender: ActorRef) = {}

  @Around("sendingMessageToActorRef(actor, message, sender)")
  def around(pjp: ProceedingJoinPoint, actor: ActorRef, message: Any, sender: ActorRef): Unit  = {

    val actorName = MetricDirectory.nameForActor(actor)
    val t = Metrics.registry.timer(actorName + "LATENCY")
    //println(s"About to proceed with: $actor $message $sender ${Kamon.context}")
    if(!actor.toString().contains("StandardOutLogger")) {
      println("Skipped the actor")
      pjp.proceedWithTarget(actor, TraceableMessage(Tracer.context, message, t.time()), sender)

    }
    else {
      println("Got the standardLogger!!")
      pjp.proceed()
    }
  }
}


@Aspect("perthis(actorCellCreation(..))")
class ActorCellInvokeInstrumentation {

  var processingTimeTimer: Timer = _
  var shouldTrack = false

  // AKKA 2.2 introduces the dispatcher parameter. Maybe we could provide a dual pointcut.

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && args(system, ref, props, dispatcher, parent)")
  def actorCellCreation(system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {}

  @After("actorCellCreation(system, ref, props, dispatcher, parent)")
  def registerMetricsInRegistry(system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {
    val actorName = MetricDirectory.nameForActor(ref)
    val histogramName = MetricDirectory.nameForMailbox(system.name, actorName)

    println("=====> Created ActorCell for: "+ref.toString())
    /** TODO: Find a better way to filter the things we don't want to measure. */
    //if(system.name != "kamon" && actorName.startsWith("/user")) {
      processingTimeTimer = Metrics.registry.timer(histogramName + "/PROCESSINGTIME")
      shouldTrack = true
    //}
  }


  @Pointcut("execution(* akka.actor.ActorCell.invoke(*)) && args(envelope)")
  def invokingActorBehaviourAtActorCell(envelope: Envelope) = {}


  @Around("invokingActorBehaviourAtActorCell(envelope)")
  def around(pjp: ProceedingJoinPoint, envelope: Envelope): Unit = {
    import ProceedingJoinPointPimp._
    println("ENVELOPE --------------------->"+envelope)
    envelope match {
      case Envelope(TraceableMessage(ctx, msg, timer), sender) => {
        timer.stop()

        val originalEnvelope = envelope.copy(message = msg)

        //println("PROCESSING TIME TIMER: "+processingTimeTimer)
        val pt = processingTimeTimer.time()
        ctx match {
          case Some(c) => {
            Tracer.set(c)
            println(s"ENVELOPE ORIGINAL: [$c]---------------->"+originalEnvelope)
            pjp.proceedWith(originalEnvelope)
            Tracer.clear
          }
          case None => pjp.proceedWith(originalEnvelope)
        }
        pt.stop()
      }
      case _ => pjp.proceed
    }
  }
}
