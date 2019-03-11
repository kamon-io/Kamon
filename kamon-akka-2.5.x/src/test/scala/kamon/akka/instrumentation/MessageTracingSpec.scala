package kamon.akka.instrumentation

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.routing.{RoundRobinGroup, RoundRobinPool}
import akka.testkit.{ImplicitSender, TestKit}
import kamon.Kamon
import kamon.module.Module
import kamon.tag.Lookups
import kamon.testkit.{MetricInspection, Reconfigure, SpanInspection, TestSpanReporter}
import kamon.trace.Span
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpecLike}
import org.scalatest.time.SpanSugar._


class MessageTracingSpec extends TestKit(ActorSystem("MessageTracing")) with WordSpecLike with MetricInspection.Syntax with Matchers
  with SpanInspection with Reconfigure with BeforeAndAfterAll with ImplicitSender with Eventually with OptionValues {

  "Message tracing instrumentation" should {
    "skip filtered out actors" in {
      val traced = system.actorOf(Props[TracingTestActor], "traced-probe-1")
      val nonTraced = system.actorOf(Props[TracingTestActor], "filteredout")
      nonTraced ! "ping"
      expectMsg("pong")

      traced ! "ping"
      expectMsg("pong")

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        spanTags("component") shouldBe "akka.actor"
        span.operationName shouldBe("TracingTestActor: String")
        spanTags("akka.actor.path") shouldNot include ("filteredout")
        spanTags("akka.actor.path") should be ("MessageTracing/user/traced-probe-1")
      }
    }

    "construct span for traced actors" in {
      val traced = system.actorOf(Props[TracingTestActor], "traced")
      traced ! "ping"
      expectMsg("pong")

      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe("TracingTestActor: String")
        spanTags("component") shouldBe "akka.actor"
        spanTags("akka.system") shouldBe "MessageTracing"
        spanTags("akka.actor.path") shouldBe "MessageTracing/user/traced"
        spanTags("akka.actor.class") shouldBe "kamon.akka.instrumentation.TracingTestActor"
        spanTags("akka.actor.message-class") shouldBe "String"
      }
    }

    "create child spans for messages between traced actors" in {
      val first = system.actorOf(Props[TracingTestActor], "traced-first")
      val second = system.actorOf(Props[TracingTestActor], "traced-second")

      first ! second
      expectMsg("pong")

      // Span for the first actor message
      val firstSpanID = eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName should include("TracingTestActor: ")
        spanTags("component") shouldBe "akka.actor"
        spanTags("akka.system") shouldBe "MessageTracing"
        spanTags("akka.actor.path") shouldBe "MessageTracing/user/traced-first"
        spanTags("akka.actor.class") shouldBe "kamon.akka.instrumentation.TracingTestActor"
        spanTags("akka.actor.message-class") should include("ActorRef")

        span.id
      }

      // Span for the second actor message
      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.parentId shouldBe firstSpanID
        span.operationName should include("TracingTestActor: String")
        spanTags("component") shouldBe "akka.actor"
        spanTags("akka.system") shouldBe "MessageTracing"
        spanTags("akka.actor.path") shouldBe "MessageTracing/user/traced-second"
        spanTags("akka.actor.class") shouldBe "kamon.akka.instrumentation.TracingTestActor"
        spanTags("akka.actor.message-class") shouldBe "String"
      }
    }

    "create hierarchy of spans even across propagation-only actors" in {
      val first = system.actorOf(Props[TracingTestActor], "traced-chain-first")
      val nonInstrumented = system.actorOf(Props[TracingTestActor], "filteredout-middle")
      val last = system.actorOf(Props[TracingTestActor], "traced-chain-last")

      first ! (nonInstrumented, last)
      expectMsg("pong")

      // Span for the first actor message
      val firstSpanID = eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.operationName shouldBe("TracingTestActor: Tuple2")
        spanTags("component") shouldBe "akka.actor"
        spanTags("akka.system") shouldBe "MessageTracing"
        spanTags("akka.actor.path") shouldBe "MessageTracing/user/traced-chain-first"
        spanTags("akka.actor.class") shouldBe "kamon.akka.instrumentation.TracingTestActor"
        spanTags("akka.actor.message-class") should include("Tuple2")

        span.id
      }

      // Span for the second actor message
      eventually(timeout(2 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _
        span.parentId shouldBe firstSpanID
        span.operationName shouldBe("TracingTestActor: String")
        spanTags("component") shouldBe "akka.actor"
        spanTags("akka.system") shouldBe "MessageTracing"
        spanTags("akka.actor.path") shouldBe "MessageTracing/user/traced-chain-last"
        spanTags("akka.actor.class") shouldBe "kamon.akka.instrumentation.TracingTestActor"
        spanTags("akka.actor.message-class") shouldBe "String"
      }
    }

    "create actor message spans when behind a group router " in {
      val routee = system.actorOf(Props[TracingTestActor],"traced-routee-one")
      val router = system.actorOf(RoundRobinGroup(Vector(routee.path.toStringWithoutAddress)).props(), "nontraced-group-router")

      router ! "ping"
      expectMsg("pong")

      eventually(timeout(2 seconds)) {
        val spanTags = stringTag(reporter.nextSpan().value) _
        spanTags("component") shouldBe "akka.actor"
        spanTags("akka.actor.path") shouldNot include ("nontraced-pool-router")
        spanTags("akka.actor.path") should be ("MessageTracing/user/traced-routee-one")
      }
    }

    "create actor message spans when behind a pool router" in {
      val router = system.actorOf(Props[TracingTestActor].withRouter(RoundRobinPool(2)), "traced-pool-router")

      router ! "ping-and-wait"
      expectMsg("pong")

      eventually(timeout(2 seconds)) {
        val spanTags = stringTag(reporter.nextSpan().value) _
        spanTags("component") shouldBe "akka.actor"
        spanTags("akka.actor.path") should be ("MessageTracing/user/traced-pool-router")
      }
    }

    def stringTag(span: Span.Finished)(tag: String): String = {
      span.tags.get(Lookups.plain(tag))
    }

  }


  @volatile var registration: Module.Registration = _
  val reporter = new TestSpanReporter()

  override protected def beforeAll(): Unit = {
    enableFastSpanFlushing()
    sampleAlways()
    registration = Kamon.registerModule("reporter", reporter)
  }

  override protected def afterAll(): Unit = {
    registration.cancel()
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