package kamon.util

import akka.actor.ActorRef

import scala.concurrent.{ ExecutionContext, Future }

/**
 *  Extension for Future[ActorRef]. Try to dispatch a message to a Future[ActorRef] in the same thread if it has already
 *  completed or do the regular scheduling otherwise. Specially useful when using the ModuleSupervisor extension to
 *  create actors.
 */
object FastDispatch {
  implicit class Syntax(val target: Future[ActorRef]) extends AnyVal {

    def fastDispatch(message: Any)(implicit ec: ExecutionContext): Unit =
      if (target.isCompleted)
        target.value.get.map(_ ! message)
      else
        target.map(_ ! message)
  }

}
