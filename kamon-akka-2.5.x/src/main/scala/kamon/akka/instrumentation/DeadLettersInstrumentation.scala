package akka.kamon.instrumentation


import akka.actor.{ActorSystem, DeadLetter, UnhandledMessage}
import kamon.akka.Metrics
import org.aspectj.lang.annotation.{After, Aspect, DeclareMixin, Pointcut}

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

  @Pointcut("execution(akka.event.EventStream.new(..)) && this(eventStream) && args(system, debug)")
  def eventStreamCreation(eventStream: HasSystem, system: ActorSystem, debug: Boolean): Unit = {}

  @After("eventStreamCreation(eventStream, system, debug)")
  def aroundEventStreamCreateion(eventStream: HasSystem, system:ActorSystem, debug: Boolean): Unit = {
    eventStream.setSystem(system)
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
