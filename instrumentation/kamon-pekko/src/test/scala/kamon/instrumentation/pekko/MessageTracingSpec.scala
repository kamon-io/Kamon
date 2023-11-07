package kamon.instrumentation.pekko

import java.util.concurrent.TimeUnit
import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, Props}
import org.apache.pekko.pattern.ask
import org.apache.pekko.routing.{RoundRobinGroup, RoundRobinPool}
import org.apache.pekko.stream.{ActorMaterializer, Materializer}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import org.apache.pekko.util.Timeout
import kamon.Kamon
import kamon.tag.Lookups
import kamon.testkit.{InitAndStopKamonAfterAll, MetricInspection, Reconfigure, SpanInspection, TestSpanReporter}
import kamon.trace.Span
import org.scalactic.TimesOnInt.convertIntToRepeater
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar._
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, OptionValues}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}


class MessageTracingSpec extends TestKit(ActorSystem("MessageTracing")) with AnyWordSpecLike with MetricInspection.Syntax
  with Matchers with SpanInspection with Reconfigure with InitAndStopKamonAfterAll with ImplicitSender with Eventually
  with OptionValues with ScalaFutures with TestSpanReporter {

  "Message tracing instrumentation" should {
    "skip filtered out actors" in {
      val traced = system.actorOf(Props[TracingTestActor], "traced-probe-1")
      val nonTraced = system.actorOf(Props[TracingTestActor], "filteredout")
      nonTraced ! "ping"
      expectMsg("pong")

      traced ! "ping"
      expectMsg("pong")

      eventually(timeout(4 seconds)) {
        val span = testSpanReporter().nextSpan().value
        val spanTags = stringTag(span) _
        spanTags("component") shouldBe "pekko.actor"
        span.operationName shouldBe "tell(String)"
        spanTags("pekko.actor.path") shouldNot include ("filteredout")
        spanTags("pekko.actor.path") should be ("MessageTracing/user/traced-probe-1")
      }
    }

    "construct span for traced actors" in {
      val traced = system.actorOf(Props[TracingTestActor], "traced")
      traced ! "ping"
      expectMsg("pong")

      eventually(timeout(2 seconds)) {
        val span = testSpanReporter().nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe "tell(String)"
        spanTags("component") shouldBe "pekko.actor"
        spanTags("pekko.system") shouldBe "MessageTracing"
        spanTags("pekko.actor.path") shouldBe "MessageTracing/user/traced"
        spanTags("pekko.actor.class") shouldBe "kamon.instrumentation.pekko.TracingTestActor"
        spanTags("pekko.actor.message-class") shouldBe "String"
      }

      val pong = traced.ask("ping")(Timeout(10, TimeUnit.SECONDS))
      Await.ready(pong, 10 seconds)

      eventually(timeout(2 seconds)) {
        val span = testSpanReporter().nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe "ask(String)"
        spanTags("component") shouldBe "pekko.actor"
        spanTags("pekko.system") shouldBe "MessageTracing"
        spanTags("pekko.actor.path") shouldBe "MessageTracing/user/traced"
        spanTags("pekko.actor.class") shouldBe "kamon.instrumentation.pekko.TracingTestActor"
        spanTags("pekko.actor.message-class") shouldBe "String"
      }
    }

    "create child spans for messages between traced actors" in {
      val first = system.actorOf(Props[TracingTestActor], "traced-first")
      val second = system.actorOf(Props[TracingTestActor], "traced-second")

      first ! second
      expectMsg("pong")

      // Span for the first actor message
      val firstSpanID = eventually(timeout(4 seconds)) {
        val span = testSpanReporter().nextSpan().value
        val spanTags = stringTag(span) _

        spanTags("component") shouldBe "pekko.actor"
        spanTags("pekko.system") shouldBe "MessageTracing"
        spanTags("pekko.actor.path") shouldBe "MessageTracing/user/traced-first"
        spanTags("pekko.actor.class") shouldBe "kamon.instrumentation.pekko.TracingTestActor"
        spanTags("pekko.actor.message-class") should include("ActorRef")
        span.id
      }

      // Span for the second actor message
      eventually(timeout(4 seconds)) {
        val span = testSpanReporter().nextSpan().value
        val spanTags = stringTag(span) _
        span.parentId shouldBe firstSpanID
        span.operationName should include("tell(String)")
        spanTags("component") shouldBe "pekko.actor"
        spanTags("pekko.system") shouldBe "MessageTracing"
        spanTags("pekko.actor.path") shouldBe "MessageTracing/user/traced-second"
        spanTags("pekko.actor.class") shouldBe "kamon.instrumentation.pekko.TracingTestActor"
        spanTags("pekko.actor.message-class") shouldBe "String"
      }
    }

    "create hierarchy of spans even across propagation-only actors" in {
      val first = system.actorOf(Props[TracingTestActor], "traced-chain-first")
      val nonInstrumented = system.actorOf(Props[TracingTestActor], "filteredout-middle")
      val last = system.actorOf(Props[TracingTestActor], "traced-chain-last")

      first ! (nonInstrumented, last)
      expectMsg("pong")

      // Span for the first actor message
      val firstSpanID = eventually(timeout(4 seconds)) {
        val span = testSpanReporter().nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe "tell(Tuple2)"
        spanTags("component") shouldBe "pekko.actor"
        spanTags("pekko.system") shouldBe "MessageTracing"
        spanTags("pekko.actor.path") shouldBe "MessageTracing/user/traced-chain-first"
        spanTags("pekko.actor.class") shouldBe "kamon.instrumentation.pekko.TracingTestActor"
        spanTags("pekko.actor.message-class") should include("Tuple2")

        span.id
      }

      // Span for the second actor message
      eventually(timeout(4 seconds)) {
        val span = testSpanReporter().nextSpan().value
        val spanTags = stringTag(span) _
        span.parentId shouldBe firstSpanID
        span.operationName shouldBe "tell(String)"
        spanTags("component") shouldBe "pekko.actor"
        spanTags("pekko.system") shouldBe "MessageTracing"
        spanTags("pekko.actor.path") shouldBe "MessageTracing/user/traced-chain-last"
        spanTags("pekko.actor.class") shouldBe "kamon.instrumentation.pekko.TracingTestActor"
        spanTags("pekko.actor.message-class") shouldBe "String"
      }
    }

    "create actor message spans when behind a group router " in {
      val routee = system.actorOf(Props[TracingTestActor],"traced-routee-one")
      val router = system.actorOf(RoundRobinGroup(Vector(routee.path.toStringWithoutAddress)).props(), "nontraced-group-router")

      router ! "ping"
      expectMsg("pong")

      eventually(timeout(2 seconds)) {
        val spanTags = stringTag(testSpanReporter().nextSpan().value) _
        spanTags("component") shouldBe "pekko.actor"
        spanTags("pekko.actor.path") shouldNot include ("nontraced-pool-router")
        spanTags("pekko.actor.path") should be ("MessageTracing/user/traced-routee-one")
      }
    }

    "create actor message spans when behind a pool router" in {
      val router = system.actorOf(Props[TracingTestActor].withRouter(RoundRobinPool(2)), "traced-pool-router")

      router ! "ping-and-wait"
      expectMsg("pong")

      eventually(timeout(2 seconds)) {
        val spanTags = stringTag(testSpanReporter().nextSpan().value) _
        spanTags("component") shouldBe "pekko.actor"
        spanTags("pekko.actor.path") should be ("MessageTracing/user/traced-pool-router")
      }
    }

    "not track Pekko Streams actors" in {
      implicit val timeout: Timeout = Timeout(10 seconds)
      val actorWithMaterializer = system.actorOf(Props[ActorWithMaterializer])

      val _ = Kamon.runWithSpan(Kamon.serverSpanBuilder("wrapper", "test").start()) {
        actorWithMaterializer.ask("stream").mapTo[String]
      }
      
      5 times {
        val allSpans = testSpanReporter()
          .spans()
          .filterNot(s => s.operationName == "wrapper" || s.operationName == "ask(String)")

        allSpans shouldBe empty
        Thread.sleep(1000)
      }
    }

    def stringTag(span: Span.Finished)(tag: String): String = {
      span.tags.withTags(span.metricTags).get(Lookups.plain(tag))
    }

  }
}

class TracingTestActor extends Actor {

  override def receive: Receive = {
    case (forwardTo: ActorRef, target: ActorRef) =>
      Thread.sleep(50)
      forwardTo.forward(target)

    case forwardTo: ActorRef =>
      forwardTo.forward("ping-and-wait")

    case "ping" =>
      sender ! "pong"

    case "ping-and-wait" =>
      Thread.sleep(50)
      sender ! "pong"
  }
}

class ActorWithMaterializer extends Actor {
  implicit val mat: Materializer = Materializer(context)

  override def receive: Receive = {
    case "stream" =>
      Await.result (
        Source(1 to 10)
          .async
          .map(x => x + x)
          .runReduce(_ + _),
        5 seconds
      )

      sender() ! "done"
  }
}
