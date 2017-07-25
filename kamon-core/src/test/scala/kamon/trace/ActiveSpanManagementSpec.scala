package kamon.trace

import kamon.Kamon
import kamon.testkit.SpanInspector
import kamon.trace.Span.Annotation
import kamon.util.Clock
import org.scalatest.{Matchers, WordSpec}

class ActiveSpanManagementSpec extends WordSpec with Matchers {

  "Kamon acting as a ActiveSpanSource" should {
    "return a empty span when there is no currently active Span" in {
      inspect(Kamon.activeSpan()) shouldBe empty
    }

    "safely operate on a empty Span" in {
      val emptySpan = Kamon.activeSpan()
      val activeSpanData = inspect(Kamon.activeSpan())
      activeSpanData shouldBe empty

      emptySpan
        .setOperationName("test")
        .addBaggage("key", "value")
        .addMetricTag("key", "value")
        .addSpanTag("string", "string")
        .addSpanTag("number", 42)
        .addSpanTag("boolean-true", true)
        .addSpanTag("boolean-false", false)
        .annotate(Annotation(Clock.microTimestamp(), "event", Map("k" -> "v")))

      val baggage = emptySpan.context().baggage
      baggage.add("key", "value")
      baggage.get("key") shouldBe empty
      baggage.getAll() shouldBe empty

      Kamon.withActiveSpan(emptySpan) {
        inspect(Kamon.activeSpan()) shouldBe empty
      }

      inspect(Kamon.activeSpan()) shouldBe empty
    }

    "set a Span as active when using activate" in {
      val span = Kamon.buildSpan("mySpan").start()
      val scope = Kamon.activate(span)
      Kamon.activeSpan() shouldBe theSameInstanceAs(span)
      scope.close()
    }

    "restore the previously active Span when a scope is closed" in {
      val previouslyActiveSpan = Kamon.activeSpan()
      inspect(Kamon.activeSpan()) shouldBe empty

      val span = Kamon.buildSpan("mySpan").start()
      Kamon.withActiveSpan(span) {
        Kamon.activeSpan() shouldBe theSameInstanceAs(span)
      }

      Kamon.activeSpan() shouldBe theSameInstanceAs(previouslyActiveSpan)
    }
  }

  def inspect(span: Span): SpanInspector =
    SpanInspector(span)
}
