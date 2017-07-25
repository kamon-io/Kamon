package kamon.trace

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.testkit.{SpanBuilding, SpanInspector}
import kamon.trace.Span.TagValue
import kamon.trace.SpanContext.Source
import kamon.trace.SpanContextCodec.Format
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
        ("metric-tag" -> "value"))

      spanData.spanTags() should contain allOf(
        ("hello" -> TagValue.String("world")),
        ("kamon" -> TagValue.String("rulez")),
        ("number" -> TagValue.Number(123)),
        ("boolean" -> TagValue.True))
    }

//    "do not interfere with the currently active Span if not requested when starting a Span" in {
//      val previouslyActiveSpan = tracer.activeSpan()
//      tracer.buildSpan("myOperation").start()
//      tracer.activeSpan() should be theSameInstanceAs(previouslyActiveSpan)
//    }
//
//    "make a span active with started with the .startActive() function and restore the previous Span when deactivated" in {
//      val previouslyActiveSpan = tracer.activeSpan()
//      val activeSpan = tracer.buildSpan("myOperation").startActive()
//
//      tracer.activeSpan() shouldNot be theSameInstanceAs(previouslyActiveSpan)
//      val activeSpanData = inspect(activeSpan)
//      activeSpanData.operationName() shouldBe "myOperation"
//
//      activeSpan.deactivate()
//      tracer.activeSpan() should be theSameInstanceAs(previouslyActiveSpan)
//    }

    "not have any parent Span if there is ActiveSpan and no parent was explicitly given" in {
      val span = tracer.buildSpan("myOperation").start()
      val spanData = inspect(span)
      spanData.context().parentID shouldBe IdentityProvider.NoIdentifier
    }

    "use the currently active span as parent" in {
      val parent = tracer.buildSpan("myOperation").start()
      val child = Kamon.withActiveSpan(parent) {
        tracer.buildSpan("childOperation").asChildOf(parent).start()
      }

      val parentData = inspect(parent)
      val childData = inspect(child)
      parentData.context().spanID shouldBe childData.context().parentID
    }

    "ignore the currently active span as parent if explicitly requested" in {
      val parent = tracer.buildSpan("myOperation").start()
      val child = Kamon.withActiveSpan(parent) {
        tracer.buildSpan("childOperation").ignoreActiveSpan().start()
      }

      val childData = inspect(child)
      childData.context().parentID shouldBe IdentityProvider.NoIdentifier
    }

    "allow overriding the start timestamp for a Span" in {
      val span = tracer.buildSpan("myOperation").withStartTimestamp(100).start()
      val spanData = inspect(span)
      spanData.startTimestamp() shouldBe 100
    }

    "inject and extract a SpanContext from a TextMap carrier" in {
      val spanContext = createSpanContext()
      val injected = Kamon.inject(spanContext, Format.TextMap)
      val extractedSpanContext = Kamon.extract(Format.TextMap, injected).value

      spanContext.traceID shouldBe(extractedSpanContext.traceID)
      spanContext.spanID shouldBe(extractedSpanContext.spanID)
      spanContext.parentID shouldBe(extractedSpanContext.parentID)
      spanContext.baggage.getAll() shouldBe(extractedSpanContext.baggage.getAll())
    }

    "inject and extract a SpanContext from a TextMap carrier supplied by the caller" in {
      val spanContext = createSpanContext()
      val carrier = TextMap.Default()
      Kamon.inject(spanContext, Format.TextMap, carrier)
      val extractedSpanContext = Kamon.extract(Format.TextMap, carrier).value

      spanContext.traceID shouldBe(extractedSpanContext.traceID)
      spanContext.spanID shouldBe(extractedSpanContext.spanID)
      spanContext.parentID shouldBe(extractedSpanContext.parentID)
      spanContext.baggage.getAll() shouldBe(extractedSpanContext.baggage.getAll())
    }

    "inject and extract a SpanContext from a HttpHeaders carrier" in {
      val spanContext = createSpanContext()
      val injected = Kamon.inject(spanContext, Format.HttpHeaders)
      val extractedSpanContext = Kamon.extract(Format.HttpHeaders, injected).value

      spanContext.traceID shouldBe(extractedSpanContext.traceID)
      spanContext.spanID shouldBe(extractedSpanContext.spanID)
      spanContext.parentID shouldBe(extractedSpanContext.parentID)
      spanContext.baggage.getAll() shouldBe(extractedSpanContext.baggage.getAll())
    }

    "inject and extract a SpanContext from a HttpHeaders using a TextMap provided by the caller" in {
      val spanContext = createSpanContext()
      val carrier = TextMap.Default()
      Kamon.inject(spanContext, Format.HttpHeaders, carrier)
      val extractedSpanContext = Kamon.extract(Format.HttpHeaders, carrier).value

      spanContext.traceID shouldBe(extractedSpanContext.traceID)
      spanContext.spanID shouldBe(extractedSpanContext.spanID)
      spanContext.parentID shouldBe(extractedSpanContext.parentID)
      spanContext.baggage.getAll() shouldBe(extractedSpanContext.baggage.getAll())
    }


    "preserve the same Span and Parent identifier when creating a Span with a remote parent if join-remote-parents-with-same-span-id is enabled" in {
      val previousConfig = Kamon.config()

      Kamon.reconfigure {
        ConfigFactory.parseString("kamon.trace.join-remote-parents-with-same-span-id = yes")
          .withFallback(Kamon.config())
      }

      val remoteParent = createSpanContext().copy(source = Source.Remote)
      val childData = inspect(tracer.buildSpan("local").asChildOf(remoteParent).start())

      childData.context().traceID shouldBe remoteParent.traceID
      childData.context().parentID shouldBe remoteParent.parentID
      childData.context().spanID shouldBe remoteParent.spanID

      Kamon.reconfigure(previousConfig)
    }

  }

  val tracer: Tracer = Kamon

  def inspect(span: Span): SpanInspector =
    SpanInspector(span)

}
