package kamon.trace

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.Context
import kamon.testkit.{SpanBuilding, SpanInspector}
import kamon.trace.Span.TagValue
import org.scalatest.{Matchers, OptionValues, WordSpec}

class TracerSpec extends WordSpec with Matchers with SpanBuilding with OptionValues {

  "the Kamon tracer" should {
    "construct a minimal Span that only has a operation name" in {
      val span = tracer.buildSpan("myOperation").start()
      val spanData = inspect(span)

      spanData.operationName() shouldBe "myOperation"
      spanData.metricTags() shouldBe empty
      spanData.spanTags() shouldBe empty
    }

    "pass the operation name and tags to started Span" in {
      val span = tracer.buildSpan("myOperation")
        .withTag("both", "both")
        .withMetricTag("metric-tag", "value")
        .withMetricTag("metric-tag", "value")
        .withSpanTag("hello", "world")
        .withSpanTag("kamon", "rulez")
        .withSpanTag("number", 123)
        .withSpanTag("boolean", true)
        .start()

      val spanData = inspect(span)
      spanData.operationName() shouldBe "myOperation"
      spanData.metricTags() should contain only (
        ("metric-tag" -> "value"),
        ("both" -> "both"))

      spanData.spanTags() should contain allOf(
        ("both" -> TagValue.String("both")),
        ("hello" -> TagValue.String("world")),
        ("kamon" -> TagValue.String("rulez")),
        ("number" -> TagValue.Number(123)),
        ("boolean" -> TagValue.True))
    }

    "not have any parent Span if there is no Span in the current context and no parent was explicitly given" in {
      val span = tracer.buildSpan("myOperation").start()
      val spanData = inspect(span)
      spanData.context().parentID shouldBe IdentityProvider.NoIdentifier
    }


    "automatically take the Span from the current Context as parent" in {
      val parent = tracer.buildSpan("myOperation").start()
      val child = Kamon.withContext(Context.create(Span.ContextKey, parent)) {
        tracer.buildSpan("childOperation").asChildOf(parent).start()
      }

      val parentData = inspect(parent)
      val childData = inspect(child)
      parentData.context().spanID shouldBe childData.context().parentID
    }

    "ignore the span from the current context as parent if explicitly requested" in {
      val parent = tracer.buildSpan("myOperation").start()
      val child = Kamon.withContext(Context.create(Span.ContextKey, parent)) {
        tracer.buildSpan("childOperation").ignoreParentFromContext().start()
      }

      val childData = inspect(child)
      childData.context().parentID shouldBe IdentityProvider.NoIdentifier
    }

    "allow overriding the start timestamp for a Span" in {
      val span = tracer.buildSpan("myOperation").withStartTimestamp(100).start()
      val spanData = inspect(span)
      spanData.startTimestamp() shouldBe 100
    }

    "preserve the same Span and Parent identifier when creating a Span with a remote parent if join-remote-parents-with-same-span-id is enabled" in {
      val previousConfig = Kamon.config()

      Kamon.reconfigure {
        ConfigFactory.parseString("kamon.trace.join-remote-parents-with-same-span-id = yes")
          .withFallback(Kamon.config())
      }

      val remoteParent = Span.Remote(createSpanContext())
      val childData = inspect(tracer.buildSpan("local").asChildOf(remoteParent).start())

      childData.context().traceID shouldBe remoteParent.context.traceID
      childData.context().parentID shouldBe remoteParent.context.parentID
      childData.context().spanID shouldBe remoteParent.context.spanID

      Kamon.reconfigure(previousConfig)
    }

  }

  val tracer: Tracer = Kamon

  def inspect(span: Span): SpanInspector =
    SpanInspector(span)

}
