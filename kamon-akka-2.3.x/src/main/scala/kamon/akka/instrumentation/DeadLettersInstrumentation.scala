package akka.kamon.instrumentation

import akka.actor.{ActorSystem, DeadLetter, UnhandledMessage}
import akka.event.EventStream
import kamon.akka.Metrics
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

trait HasSystem {
  def system: ActorSystem
  def setSystem(system: ActorSystem): Unit
}

object HasSystem {
  def apply(): HasSystem = new HasSystem {
    private var _system: ActorSystem = _

    override def system: ActorSystem = _system

    override def setSystem(system: ActorSystem): Unit = _system = system
  }
}

@Aspect
class DeadLettersInstrumentation {

  @DeclareMixin("akka.event.EventStream+")
  def mixinHasSystem: HasSystem = HasSystem()

  @Pointcut("call(akka.event.EventStream.new(..)) && this(system) && args(debug)")
  def eventStreamCreation(system: ActorSystem, debug: Boolean): Unit = {}

  @Around("eventStreamCreation(system, debug)")
  def aroundEventStreamCreateion(pjp: ProceedingJoinPoint, system: ActorSystem, debug: Boolean): EventStream = {
    val stream = pjp.proceed().asInstanceOf[EventStream]
    stream.asInstanceOf[HasSystem].setSystem(system)
    stream
  }


  @Pointcut("execution(* akka.event.EventStream.publish(..)) && this(stream) && args(event)")
  def streamPublish(stream: HasSystem, event: AnyRef): Unit = {}

  @After("streamPublish(stream, event)")
  def afterStreamSubchannel(stream: HasSystem, event: AnyRef): Unit = trackEvent(stream, event)


  private def trackEvent(stream: HasSystem, event: AnyRef): Unit = event match {
    case dl: DeadLetter => Metrics.forSystem(stream.system.name).deadLetters.increment()
    case um: UnhandledMessage => Metrics.forSystem(stream.system.name).unhandledMessages.increment()
    case _ => ()
  }

}
