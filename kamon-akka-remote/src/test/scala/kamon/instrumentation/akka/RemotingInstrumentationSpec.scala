package kamon.instrumentation.akka

import akka.actor.SupervisorStrategy.Resume
import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.remote.RemoteScope
import akka.routing.RoundRobinGroup
import akka.testkit.{ ImplicitSender, TestKitBase }
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import kamon.trace.TraceRecorder
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._
import scala.util.control.NonFatal

class RemotingInstrumentationSpec extends TestKitBase with WordSpecLike with Matchers with ImplicitSender {
  implicit lazy val system: ActorSystem = ActorSystem("remoting-spec-local-system", ConfigFactory.parseString(
    """
      |akka {
      |  actor {
      |    provider = "akka.remote.RemoteActorRefProvider"
      |  }
      |  remote {
      |    enabled-transports = ["akka.remote.netty.tcp"]
      |    netty.tcp {
      |      hostname = "127.0.0.1"
      |      port = 2552
      |    }
      |  }
      |}
    """.stripMargin))

  val remoteSystem: ActorSystem = ActorSystem("remoting-spec-remote-system", ConfigFactory.parseString(
    """
      |akka {
      |  actor {
      |    provider = "akka.remote.RemoteActorRefProvider"
      |  }
      |  remote {
      |    enabled-transports = ["akka.remote.netty.tcp"]
      |    netty.tcp {
      |      hostname = "127.0.0.1"
      |      port = 2553
      |    }
      |  }
      |}
    """.stripMargin))

  val RemoteSystemAddress = AddressFromURIString("akka.tcp://remoting-spec-remote-system@127.0.0.1:2553")

  "The Remoting instrumentation" should {
    "propagate the TraceContext when creating a new remote actor" in {
      TraceRecorder.withNewTraceContext("deploy-remote-actor", Some("deploy-remote-actor-1")) {
        system.actorOf(TraceTokenReplier.remoteProps(Some(testActor), RemoteSystemAddress), "remote-deploy-fixture")
      }

      expectMsg("name=deploy-remote-actor|token=deploy-remote-actor-1|isOpen=true")
    }

    "propagate the TraceContext when sending a message to a remotely deployed actor" in {
      val remoteRef = system.actorOf(TraceTokenReplier.remoteProps(None, RemoteSystemAddress), "remote-message-fixture")

      TraceRecorder.withNewTraceContext("message-remote-actor", Some("message-remote-actor-1")) {
        remoteRef ! "reply-trace-token"
      }

      expectMsg("name=message-remote-actor|token=message-remote-actor-1|isOpen=true")
    }

    "propagate the TraceContext when pipe or ask a message to a remotely deployed actor" in {
      implicit val ec = system.dispatcher
      implicit val askTimeout = Timeout(10 seconds)
      val remoteRef = system.actorOf(TraceTokenReplier.remoteProps(None, RemoteSystemAddress), "remote-ask-and-pipe-fixture")

      TraceRecorder.withNewTraceContext("ask-and-pipe-remote-actor", Some("ask-and-pipe-remote-actor-1")) {
        (remoteRef ? "reply-trace-token") pipeTo (testActor)
      }

      expectMsg("name=ask-and-pipe-remote-actor|token=ask-and-pipe-remote-actor-1|isOpen=true")
    }

    "propagate the TraceContext when sending a message to an ActorSelection" in {
      remoteSystem.actorOf(TraceTokenReplier.props(None), "actor-selection-target-a")
      remoteSystem.actorOf(TraceTokenReplier.props(None), "actor-selection-target-b")
      val selection = system.actorSelection(RemoteSystemAddress + "/user/actor-selection-target-*")

      TraceRecorder.withNewTraceContext("message-remote-actor-selection", Some("message-remote-actor-selection-1")) {
        selection ! "reply-trace-token"
      }

      // one for each selected actor
      expectMsg("name=message-remote-actor-selection|token=message-remote-actor-selection-1|isOpen=true")
      expectMsg("name=message-remote-actor-selection|token=message-remote-actor-selection-1|isOpen=true")
    }

    "propagate the TraceContext a remotely supervised child fails" in {
      val supervisor = system.actorOf(Props(new SupervisorOfRemote(testActor, RemoteSystemAddress)))

      TraceRecorder.withNewTraceContext("remote-supervision", Some("remote-supervision-1")) {
        supervisor ! "fail"
      }

      expectMsg("name=remote-supervision|token=remote-supervision-1|isOpen=true")
    }

    "propagate the TraceContext when sending messages to remote routees of a router" in {
      remoteSystem.actorOf(TraceTokenReplier.props(None), "remote-routee")
      val router = system.actorOf(RoundRobinGroup(List(RemoteSystemAddress + "/user/actor-selection-target-*")).props(), "router")

      TraceRecorder.withNewTraceContext("remote-routee", Some("remote-routee-1")) {
        router ! "reply-trace-token"
      }

      expectMsg("name=remote-routee|token=remote-routee-1|isOpen=true")
    }
  }

}

class TraceTokenReplier(creationTraceContextListener: Option[ActorRef]) extends Actor with ActorLogging {
  creationTraceContextListener map { recipient ⇒
    recipient ! currentTraceContextInfo
  }

  def receive = {
    case "fail" ⇒
      throw new ArithmeticException("Division by zero.")
    case "reply-trace-token" ⇒
      log.info("Sending back the TT: " + TraceRecorder.currentContext.token)
      sender ! currentTraceContextInfo
  }

  def currentTraceContextInfo: String = {
    val ctx = TraceRecorder.currentContext
    s"name=${ctx.name}|token=${ctx.token}|isOpen=${ctx.isOpen}"
  }
}

object TraceTokenReplier {
  def props(creationTraceContextListener: Option[ActorRef]): Props =
    Props(new TraceTokenReplier(creationTraceContextListener))

  def remoteProps(creationTraceContextListener: Option[ActorRef], remoteAddress: Address): Props = {
    Props(new TraceTokenReplier(creationTraceContextListener))
      .withDeploy(Deploy(scope = RemoteScope(remoteAddress)))
  }
}

class SupervisorOfRemote(traceContextListener: ActorRef, remoteAddress: Address) extends Actor {
  val supervisedChild = context.actorOf(TraceTokenReplier.remoteProps(None, remoteAddress), "remotely-supervised-child")

  def receive = {
    case "fail" ⇒ supervisedChild ! "fail"
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case NonFatal(throwable) ⇒
      traceContextListener ! currentTraceContextInfo
      Resume
  }

  def currentTraceContextInfo: String = {
    val ctx = TraceRecorder.currentContext
    s"name=${ctx.name}|token=${ctx.token}|isOpen=${ctx.isOpen}"
  }
}
