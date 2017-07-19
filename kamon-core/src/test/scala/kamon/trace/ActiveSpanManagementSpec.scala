package kamon.trace

import kamon.Kamon
import kamon.testkit.SpanInspector
import kamon.trace.Span.Annotation
import kamon.util.Clock
import org.scalatest.{Matchers, WordSpec}

class ActiveSpanManagementSpec extends WordSpec with Matchers {

  "Kamon acting as a ActiveSpanSource" should {
    "return a ActiveSpan wrapping a empty span when there is no currently active Span" in {
      inspect(Kamon.activeSpan()) shouldBe empty
    }

    "safely operate on a ActiveSpan that wraps a empty Span" in {
      val activeSpan = Kamon.activeSpan()
      val activeSpanData = inspect(Kamon.activeSpan())
      activeSpanData shouldBe empty

      activeSpan
        .setOperationName("test")
        .addBaggage("key", "value")
        .addMetricTag("key", "value")
        .addSpanTag("string", "string")
        .addSpanTag("number", 42)
        .addSpanTag("boolean-true", true)
        .addSpanTag("boolean-false", false)
        .annotate(Annotation(Clock.microTimestamp(), "event", Map("k" -> "v")))

      val baggage = activeSpan.context().baggage
      baggage.add("key", "value")
      baggage.get("key") shouldBe empty
      baggage.getAll() shouldBe empty

      val continuation = activeSpan.capture()
      val activatedSpan = continuation.activate()
      inspect(Kamon.activeSpan()) shouldBe empty
      activatedSpan.deactivate()

      inspect(Kamon.activeSpan()) shouldBe empty
    }

    "set a Span as active when using makeActive" in {
      val span = Kamon.buildSpan("mySpan").start()
      val activeSpan = Kamon.makeActive(span)
      Kamon.activeSpan() shouldBe theSameInstanceAs(activeSpan)
      activeSpan.deactivate()
    }

    "set a Span as active when using startActive" in {
      val activeSpan = Kamon.buildSpan("mySpan").startActive()
      Kamon.activeSpan() shouldBe theSameInstanceAs(activeSpan)
      activeSpan.deactivate()
    }

    "restore the previously active Span when a ActiveSpan gets deactivated" in {
      val previouslyActiveSpan = Kamon.activeSpan()
      inspect(Kamon.activeSpan()) shouldBe empty

      val activeSpan = Kamon.buildSpan("mySpan").startActive()
      Kamon.activeSpan() shouldBe theSameInstanceAs(activeSpan)
      activeSpan.deactivate()

      Kamon.activeSpan() shouldBe theSameInstanceAs(previouslyActiveSpan)
    }
  }

  def inspect(span: Span): SpanInspector =
    SpanInspector(span)
}
