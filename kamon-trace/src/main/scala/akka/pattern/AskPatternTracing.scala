package akka.pattern

import org.aspectj.lang.annotation.{AfterReturning, Pointcut, Aspect}
import akka.event.Logging.Warning
import scala.compat.Platform.EOL
import akka.actor.ActorRefProvider

@Aspect
class AskPatternTracing {

  class StackTraceCaptureException extends Throwable

  @Pointcut(value = "execution(* akka.pattern.PromiseActorRef$.apply(..)) && args(provider, *)", argNames = "provider")
  def promiseActorRefApply(provider: ActorRefProvider): Unit = {
    provider.settings.config.getBoolean("kamon.trace.ask-pattern-tracing")
  }

  @AfterReturning(pointcut = "promiseActorRefApply(provider)", returning = "promiseActor")
  def hookAskTimeoutWarning(provider: ActorRefProvider, promiseActor: PromiseActorRef): Unit = {
    val future = promiseActor.result.future
    val system = promiseActor.provider.guardian.underlying.system
    implicit val ec = system.dispatcher
    val stack = new StackTraceCaptureException

    future onFailure {
      case timeout: AskTimeoutException =>
        val stackString = stack.getStackTrace.drop(3).mkString("", EOL, EOL)

        system.eventStream.publish(Warning("AskPatternTracing", classOf[AskPatternTracing],
          "Timeout triggered for ask pattern registered at: " + stackString))
    }
  }
}
