package kamon.akka.instrumentation

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.routing.RoundRobinGroup
import akka.testkit.{ImplicitSender, TestKit}
import kamon.Kamon
import kamon.testkit.{MetricInspection, SpanInspection}
import kamon.trace.Span
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}


class RootActor(tester: Option[ActorRef]) extends Actor with SpanInspection {

  def currentSpan = Kamon.currentContext().get(Span.ContextKey)

  override def receive: Receive = {
    case (forwardTo: ActorRef, target: ActorRef) => {
      tester.foreach(_ ! currentSpan)
      forwardTo ! target
    }
    case forwardTo: ActorRef => {
      tester.foreach(_ ! currentSpan)
      forwardTo ! "span"
    }
    case "span" => {
      tester.foreach(_ ! currentSpan)
    }

  }
}

class MessageTracingSpec extends TestKit(ActorSystem("MessageTracing")) with WordSpecLike with MetricInspection with Matchers with SpanInspection
  with BeforeAndAfterAll with ImplicitSender with Eventually {

  "Message tracing instrumentation" should {

    "skip filtered out actors" in {
      val traced = system.actorOf(Props(classOf[RootActor], Some(testActor)), "filteredout")
      traced ! "span"
      assert(expectMsgType[Span] == Span.Empty)
    }

    "construct span for traced actors" in {
      val traced = system.actorOf(Props(classOf[RootActor], Some(testActor)), "traced")
      traced ! "span"
      val spanCtx = inspect(expectMsgType[Span]).context()
      assert(spanCtx.parentID.string.isEmpty)
      assert(spanCtx.spanID.string.nonEmpty)
      assert(spanCtx.traceID.string.nonEmpty)
    }

    "create child spans for messages between traced actors" in {
      val root = system.actorOf(Props(classOf[RootActor], Some(testActor)), "traced-root")
      val child = system.actorOf(Props(classOf[RootActor], Some(testActor)), "traced-child")

      root ! child

      val rootSpan = inspect(expectMsgType[Span]).context()
      val childSpan = inspect(expectMsgType[Span]).context()

      assert(rootSpan.parentID.string.isEmpty)
      assert(childSpan.parentID.string == rootSpan.spanID.string)
    }

    "create hierarchy of spans even across propagation-only actors" in {
      val root = system.actorOf(Props(classOf[RootActor], Some(testActor)), "traced-chain-root")
      val noninstrumented = system.actorOf(Props(classOf[RootActor], Some(testActor)), "filteredout-child")
      val child = system.actorOf(Props(classOf[RootActor], Some(testActor)), "traced-chain-child")

      root ! (noninstrumented, child)

      val rootSpan = inspect(expectMsgType[Span]).context()
      val nonInstrumented = inspect(expectMsgType[Span]).context()
      val childSpan = inspect(expectMsgType[Span]).context()

      assert(rootSpan.parentID.string.isEmpty)
      assert(nonInstrumented.spanID.string == rootSpan.spanID.string)
      assert(childSpan.parentID.string == rootSpan.spanID.string)
    }

    "create routee spans as child of original when router is not traced" in {
      val root = system.actorOf(Props(classOf[RootActor], Some(testActor)),"traced-router-root")
      val routee = system.actorOf(Props(classOf[RootActor], Some(testActor)),"traced-routee-one")
      val router = system.actorOf(RoundRobinGroup(Vector(routee.path.toStringWithoutAddress)).props(), "nontraced-pool-router")

      root ! router

      val rootSpan = inspect(expectMsgType[Span]).context()
      val routeeOne = inspect(expectMsgType[Span]).context()

      assert(rootSpan.spanID.string == routeeOne.parentID.string)
    }

    "create parent-child spans for router-routee when both are traced" in {
      val routee = system.actorOf(Props(classOf[RootActor], Some(testActor)),"traced-routee")
      val router = system.actorOf(RoundRobinGroup(Vector(routee.path.toStringWithoutAddress)).props(), "traced-pool-router")

      router ! "span"

      val routeeSpan = inspect(expectMsgType[Span]).context()

      assert(routeeSpan.parentID.string.nonEmpty)
    }

  }



}
