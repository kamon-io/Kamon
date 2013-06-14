package kamon.instrumentation

import org.aspectj.lang.annotation.{Before, Around, Pointcut, Aspect}
import org.aspectj.lang.ProceedingJoinPoint
import akka.actor.{Props, ActorSystem, ActorRef}
import kamon.{Kamon, TraceContext}
import akka.dispatch.Envelope
import com.codahale.metrics.{ExponentiallyDecayingReservoir, Histogram}
import kamon.metric.{MetricDirectory, Metrics}

case class TraceableMessage(traceContext: Option[TraceContext], message: Any, timeStamp: Long = System.nanoTime())


@Aspect
class ActorRefTellInstrumentation {
  import ProceedingJoinPointPimp._

  @Pointcut("execution(* akka.actor.ScalaActorRef+.$bang(..)) && !within(akka.pattern.PromiseActorRef) && args(message, sender)")
  def sendingMessageToActorRef(message: Any, sender: ActorRef) = {}

  @Around("sendingMessageToActorRef(message, sender)")
  def around(pjp: ProceedingJoinPoint, message: Any, sender: ActorRef): Unit  = pjp.proceedWith(TraceableMessage(Kamon.context, message))
}


@Aspect("perthis(actorCellCreation(..))")
class ActorCellInvokeInstrumentation {

  val latencyHistogram: Histogram = new Histogram(new ExponentiallyDecayingReservoir)
  var shouldTrack = false

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && args(system, ref, props, parent)")
  def actorCellCreation(system: ActorSystem, ref: ActorRef, props: Props, parent: ActorRef): Unit = {}

  @Before("actorCellCreation(system, ref, props, parent)")
  def registerMetricsInRegistry(system: ActorSystem, ref: ActorRef, props: Props, parent: ActorRef): Unit = {
    val actorName = MetricDirectory.nameForActor(ref)
    val histogramName = MetricDirectory.nameForMailbox(system.name, actorName)

    // TODO: Find a better way to filter the thins we don't want to measure.
    if(system.name != "kamon" && actorName.startsWith("/user")) {
      Metrics.registry.register(histogramName + "/cell", latencyHistogram)
      shouldTrack = true
    }
  }



  @Pointcut("execution(* akka.actor.ActorCell.invoke(*)) && args(envelope)")
  def invokingActorBehaviourAtActorCell(envelope: Envelope) = {}


  @Around("invokingActorBehaviourAtActorCell(envelope)")
  def around(pjp: ProceedingJoinPoint, envelope: Envelope): Unit = {
    import ProceedingJoinPointPimp._

    envelope match {
      case Envelope(TraceableMessage(ctx, msg, timeStamp), sender) => {
        latencyHistogram.update(System.nanoTime() - timeStamp)

        val originalEnvelope = envelope.copy(message = msg)
        ctx match {
          case Some(c) => {
            Kamon.set(c)
            pjp.proceedWith(originalEnvelope)
            Kamon.clear
          }
          case None => pjp.proceedWith(originalEnvelope)
        }
      }
      case _ => pjp.proceed
    }
  }
}
