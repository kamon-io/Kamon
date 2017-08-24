package kamon.trace

import kamon.Kamon
import kamon.context.Context
import kamon.testkit.SpanInspection
import org.scalatest.{Matchers, WordSpec}

class SpanCustomizerSpec extends WordSpec with Matchers with SpanInspection {

  "a SpanCustomizer" should {
    "default to a Noop implementation when none is in the context" in {
      val noopCustomizer = Context.Empty.get(SpanCustomizer.ContextKey)
      val spanBuilder = noopCustomizer.customize(Kamon.buildSpan("noop"))
      val span = inspect(spanBuilder.start())

      span.operationName() shouldBe "noop"
      span.metricTags() shouldBe empty
      span.spanTags() shouldBe empty
    }

    "have a simple builder for customizing the operation name" in {
      val operationNameCustomizer = SpanCustomizer.forOperationName("myCustomOperationName")
      val spanBuilder = operationNameCustomizer.customize(Kamon.buildSpan("noop"))
      val span = inspect(spanBuilder.start())

      span.operationName() shouldBe "myCustomOperationName"
      span.metricTags() shouldBe empty
      span.spanTags() shouldBe empty
    }
  }
}
