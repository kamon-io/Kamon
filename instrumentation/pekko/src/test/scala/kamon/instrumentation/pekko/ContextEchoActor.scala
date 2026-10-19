package kamon.instrumentation.pekko

import org.apache.pekko.actor._
import org.apache.pekko.remote.RemoteScope
import kamon.Kamon
import kamon.tag.Lookups._

class ContextEchoActor(creationListener: Option[ActorRef]) extends Actor with ActorLogging {

  creationListener foreach { recipient =>
    recipient ! currentTraceContextInfo
  }

  def receive = {
    case "die" =>
      throw new ArithmeticException("Division by zero.")

    case "reply-trace-token" =>
      sender ! currentTraceContextInfo
  }

  def currentTraceContextInfo: String = {
    val ctx = Kamon.currentContext()
    val name = ctx.getTag(option(ContextEchoActor.EchoTag)).getOrElse("")
    s"name=$name"
  }
}

object ContextEchoActor {

  val EchoTag = "tests"

  def props(creationListener: Option[ActorRef]): Props =
    Props(classOf[ContextEchoActor], creationListener)

  def remoteProps(creationTraceContextListener: Option[ActorRef], remoteAddress: Address): Props =
    Props(classOf[ContextEchoActor], creationTraceContextListener)
      .withDeploy(Deploy(scope = RemoteScope(remoteAddress)))

}
