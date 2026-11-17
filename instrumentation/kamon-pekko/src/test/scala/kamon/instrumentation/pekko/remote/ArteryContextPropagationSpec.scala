package kamon.instrumentation.pekko.remote

import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, ExtendedActorSystem, Props, RootActorPath}
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.Context
import kamon.testkit.InitAndStopKamonAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

/**
  * Each message sent over Artery must arrive with the Context it was sent with: a message sent under a Span arrives under
  * that Span's trace and a message sent without one arrives without a trace.
  */
class ArteryContextPropagationSpec extends AnyWordSpec with Matchers with InitAndStopKamonAfterAll with Eventually {
  import ArteryContextPropagationSpec._

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(10, Seconds))

  private val received = new ConcurrentLinkedQueue[Received]()

  private def remotingSystem(name: String): ActorSystem = ActorSystem(
    name,
    ConfigFactory
      .parseString("""
        |pekko {
        |  loglevel = ERROR // the remoting connections log warnings when the systems shut down
        |  actor.provider = remote
        |  remote.artery.canonical {
        |    hostname = "127.0.0.1"
        |    port = 0
        |  }
        |}
      """.stripMargin)
      .withFallback(ConfigFactory.load())
  )

  private lazy val senderSystem = remotingSystem("artery-context-sender")
  private lazy val receiverSystem = remotingSystem("artery-context-receiver")

  private lazy val recorder: ActorRef = {
    receiverSystem.actorOf(Props(new Recorder(received)), "context-recorder")
    val address = receiverSystem.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress
    Await.result(
      senderSystem.actorSelection(RootActorPath(address) / "user" / "context-recorder").resolveOne(10.seconds),
      15.seconds
    )
  }

  override protected def afterAll(): Unit = {
    senderSystem.terminate()
    receiverSystem.terminate()
    Await.ready(senderSystem.whenTerminated, 15.seconds)
    Await.ready(receiverSystem.whenTerminated, 15.seconds)
    super.afterAll()
  }

  /** Sends the message to the remote recorder, under a new Span if traced, and returns the trace it should arrive with. */
  private def send(message: String, traced: Boolean): Expected = {
    Kamon.currentContext() shouldBe Context.Empty
    if (traced) {
      val span = Kamon.spanBuilder("artery-context-propagation").start()
      Kamon.runWithSpan(span)(recorder ! message)
      Expected(message, Some(span.trace.id.string))
    } else {
      recorder ! message
      Expected(message, None)
    }
  }

  private def awaitArrival(expected: Seq[Expected]): Unit =
    eventually(received.asScala.map(_.message).toSet should contain allElementsOf expected.map(_.message))

  private def wronglyTraced(expected: Seq[Expected]): Seq[(Expected, Option[String])] = {
    val arrived = received.asScala.map(r => r.message -> r.traceId).toMap
    expected.map(e => e -> arrived(e.message)).filter { case (e, traceId) => e.traceId != traceId }
  }

  "the Artery instrumentation" should {
    "not dispatch a message sent without a Context with the Context of the previous message that used its envelope" in {
      // One message at a time, so that each message reuses the pooled inbound envelope released by the previous one
      val expected = (1 to 20).flatMap { i =>
        val traced = send(s"sequential-traced-$i", traced = true)
        awaitArrival(Seq(traced))
        val untraced = send(s"sequential-untraced-$i", traced = false)
        awaitArrival(Seq(untraced))
        Seq(traced, untraced)
      }

      wronglyTraced(expected) shouldBe empty
    }

    "write each message with its own Context when a burst of messages is serialized together" in {
      // A burst is serialized by the outbound stream in one go, after one of the senders woke the stream up
      val expected = (1 to 20).flatMap { i =>
        val burst = (1 to 8).map(j => send(s"burst-$i-$j", traced = j % 3 == 1))
        awaitArrival(burst)
        burst
      }

      wronglyTraced(expected) shouldBe empty
    }
  }
}

object ArteryContextPropagationSpec {
  final case class Received(message: String, traceId: Option[String])
  final case class Expected(message: String, traceId: Option[String])

  final class Recorder(received: ConcurrentLinkedQueue[Received]) extends Actor {
    override def receive: Receive = { case message: String =>
      val span = Kamon.currentSpan()
      received.add(Received(message, if (span.isEmpty) None else Some(span.trace.id.string)))
    }
  }
}
