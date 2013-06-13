package akka.instrumentation

import org.aspectj.lang.annotation.{Before, Around, Pointcut, Aspect}
import org.aspectj.lang.ProceedingJoinPoint
import akka.actor.{Props, ActorSystem, ActorRef}
import kamon.{Kamon, TraceContext}
import akka.dispatch.Envelope
import com.codahale.metrics.{ExponentiallyDecayingReservoir, Histogram}
import kamon.metric.{MetricDirectory, Metrics}

case class TraceableEnvelope(traceContext: TraceContext, message: Any, timeStamp: Long = System.nanoTime())


@Aspect
class ActorRefTellInstrumentation {
  println("Created ActorAspect")

  @Pointcut("execution(* akka.actor.ScalaActorRef+.$bang(..)) && !within(akka.pattern.PromiseActorRef) && args(message, sender)")
  def sendingMessageToActorRef(message: Any, sender: ActorRef) = {}

  @Around("sendingMessageToActorRef(message, sender)")
  def around(pjp: ProceedingJoinPoint, message: Any, sender: ActorRef): Unit  = {
    import pjp._

    Kamon.context() match {
      case Some(ctx) => {
        val traceableMessage = TraceableEnvelope(ctx, message)

        // update the args with the new message
        val args = getArgs
        args.update(0, traceableMessage)
        proceed(args)
      }
      case None => proceed
    }
  }
}


@Aspect("perthis(actorCellCreation(..))")
class ActorCellInvokeInstrumentation {

  val latencyHistogram: Histogram = new Histogram(new ExponentiallyDecayingReservoir)
  val messagesPer
  @volatile var shouldTrack = false

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
    import pjp._

    envelope match {
      case Envelope(TraceableEnvelope(ctx, msg, timeStamp), sender) => {
        latencyHistogram.update(System.nanoTime() - timeStamp)

        Kamon.set(ctx)

        val originalEnvelope = envelope.copy(message = msg)
        proceed(getArgs.updated(0, originalEnvelope))

        Kamon.clear
      }
      case _ => proceed
    }
  }
}
