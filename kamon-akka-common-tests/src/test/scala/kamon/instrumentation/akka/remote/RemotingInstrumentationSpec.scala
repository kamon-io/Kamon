package kamon.instrumentation.akka.remote

import akka.actor.SupervisorStrategy.Resume
import akka.actor._
import akka.pattern.{ask, pipe}
import akka.routing.RoundRobinGroup
import akka.testkit.{ImplicitSender, TestKitBase}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.akka.AkkaRemoteMetrics.{DeserializationTime, SerializationTime}
import kamon.instrumentation.akka.{AkkaRemoteMetrics, ContextEchoActor}
import kamon.tag.Lookups._
import kamon.tag.TagSet
import kamon.testkit.{InstrumentInspection, MetricInspection}
import org.scalatest.Inspectors._
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.util.control.NonFatal

class RemotingInstrumentationSpec extends TestKitBase with WordSpecLike with Matchers with ImplicitSender
    with MetricInspection.Syntax with InstrumentInspection.Syntax {

  implicit lazy val system: ActorSystem = {
    ActorSystem("remoting-spec-local-system", ConfigFactory.parseString(
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
  }

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

  def contextWithBroadcast(name: String): Context =
    Context.Empty.withTag(
      ContextEchoActor.EchoTag,
      name
    )

  "The Akka Remote instrumentation" should {
    "propagate the current Context when creating a new remote actor" in {
      val a = Kamon.storeContext(contextWithBroadcast("deploy-remote-actor-1")) {
        system.actorOf(ContextEchoActor.remoteProps(Some(testActor), RemoteSystemAddress), "remote-deploy-fixture")
      }

      expectMsg(10 seconds, "name=deploy-remote-actor-1")
    }


    "propagate the Context when sending a message to a remotely deployed actor" in {
      val remoteRef = system.actorOf(ContextEchoActor.remoteProps(None, RemoteSystemAddress), "remote-message-fixture")

      Kamon.storeContext(contextWithBroadcast("message-remote-actor-1")) {
        remoteRef ! "reply-trace-token"
      }
      expectMsg("name=message-remote-actor-1")
    }


    "propagate the current Context when pipe or ask a message to a remotely deployed actor" in {
      implicit val ec = system.dispatcher
      implicit val askTimeout = Timeout(10 seconds)
      val remoteRef = system.actorOf(ContextEchoActor.remoteProps(None, RemoteSystemAddress), "remote-ask-and-pipe-fixture")

      Kamon.storeContext(contextWithBroadcast("ask-and-pipe-remote-actor-1")) {
        (remoteRef ? "reply-trace-token") pipeTo testActor
      }

      expectMsg("name=ask-and-pipe-remote-actor-1")
    }


    "propagate the current Context when sending a message to an ActorSelection" in {
      remoteSystem.actorOf(ContextEchoActor.props(None), "actor-selection-target-a")
      remoteSystem.actorOf(ContextEchoActor.props(None), "actor-selection-target-b")
      val selection = system.actorSelection(RemoteSystemAddress + "/user/actor-selection-target-*")

      Kamon.storeContext(contextWithBroadcast("message-remote-actor-selection-1")) {
        selection ! "reply-trace-token"
      }

      // one for each selected actor
      expectMsg("name=message-remote-actor-selection-1")
      expectMsg("name=message-remote-actor-selection-1")
    }

    "propagate the current Context when sending messages to remote routees of a router" in {
      remoteSystem.actorOf(ContextEchoActor.props(None), "router-target-a")
      remoteSystem.actorOf(ContextEchoActor.props(None), "router-target-b")
      val router = system.actorOf(RoundRobinGroup(List(
        RemoteSystemAddress + "/user/router-target-a",
        RemoteSystemAddress + "/user/router-target-b"
      )).props(), "router")

      Kamon.storeContext(contextWithBroadcast("remote-routee-1")) {
        router ! "reply-trace-token"
      }

      expectMsg("name=remote-routee-1")
    }

    "propagate the current Context when a remotely supervised child fails" in {
      val supervisor = system.actorOf(Props(new SupervisorOfRemote(testActor, RemoteSystemAddress)),"SUPERVISOR")

      Kamon.storeContext(contextWithBroadcast("remote-supervision-1")) {
        supervisor ! "fail"
      }

      expectMsg(2 minutes,"name=remote-supervision-1")
    }

    "record in/out message counts and sizes for both sending and receiving side" in {
      val (out, in) = (
        AkkaRemoteMetrics.OutboundMessageSize.withTags(TagSet.of("system", system.name)).distribution(false),
        AkkaRemoteMetrics.OutboundMessageSize.withTags(TagSet.of("system", system.name)).distribution(false)
      )

      assert(out.max > 0)
      assert(in.max > 0)
      assert(out.count > 0)
      assert(in.count > 0)
    }

    "record de/serialization times for messages" in {
      val systems = Seq(system.name, remoteSystem.name)
      val serializationTimes = systems.map(s => SerializationTime.withTags(TagSet.of("system", s)).distribution().count)
      val deserializationTimes = systems.map(s => DeserializationTime.withTags(TagSet.of("system", s)).distribution().count)

      forAll(serializationTimes ++ deserializationTimes) { count => assert(count > 0) }
    }
  }
}

class SupervisorOfRemote(echoListener: ActorRef, remoteAddress: Address) extends Actor {
  val supervisedChild = context.actorOf(ContextEchoActor.remoteProps(None, remoteAddress), "remotely-supervised-child")

  def receive = {
    case "fail" =>  supervisedChild ! "die"
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case NonFatal(_) =>
      echoListener ! currentEchoMessage
      Resume
    case _ => Resume
  }

  def currentEchoMessage: String = {
    val ctx = Kamon.currentContext()
    val name = ctx.getTag(option(ContextEchoActor.EchoTag)).getOrElse("")
    s"name=$name"
  }
}