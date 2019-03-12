/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.trace

import java.time.Instant

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.Context
import kamon.testkit.{SpanBuilding, SpanInspection}
import kamon.trace.Span.TagValue
import kamon.trace.SpanContext.SamplingDecision
import org.scalatest.{Matchers, OptionValues, WordSpec}

// A custom sampler for testing, samples all operations that start with a specific prefix
object TestSampler {
  val MagicPrefix = "magic:"
}

class TestSampler extends Sampler {

  // Ensure we could access the configuration if necessary
  assert(Kamon.config().getString("kamon.trace.custom-sampler") == getClass.getCanonicalName,
    "Custom Sampler should have access to Kamon Config"
  )

  def decide(operationName: String, builderTags: Map[String, Span.TagValue]): SamplingDecision =
    if (operationName.startsWith(TestSampler.MagicPrefix)) SamplingDecision.Sample else SamplingDecision.DoNotSample
}

class TracerSpec extends WordSpec with Matchers with SpanBuilding with SpanInspection with OptionValues {

  "the Kamon tracer" should {
    "construct a minimal Span that only has a operation name" in {
      val span = Kamon.buildSpan("myOperation").start()
      val spanData = inspect(span)

      spanData.operationName() shouldBe "myOperation"
      spanData.metricTags() shouldBe empty
      spanData.spanTags() shouldBe empty
    }

    "pass the operation name and tags to started Span" in {
      val span = Kamon.buildSpan("myOperation")
        .withMetricTag("metric-tag", "value")
        .withMetricTag("metric-tag", "value")
        .withTag("hello", "world")
        .withTag("kamon", "rulez")
        .withTag("number", 123)
        .withTag("boolean", true)
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

    "not have any parent Span if there is no Span in the current context and no parent was explicitly given" in {
      val span = Kamon.buildSpan("myOperation").start()
      val spanData = inspect(span)
      spanData.context().parentID shouldBe IdentityProvider.NoIdentifier
    }


    "automatically take the Span from the current Context as parent" in {
      val parent = Kamon.buildSpan("myOperation").start()
      val child = Kamon.withSpan(parent) {
        Kamon.buildSpan("childOperation").asChildOf(parent).start()
      }

      val parentData = inspect(parent)
      val childData = inspect(child)
      parentData.context().spanID shouldBe childData.context().parentID
    }

    "ignore the span from the current context as parent if explicitly requested" in {
      val parent = Kamon.buildSpan("myOperation").start()
      val child = Kamon.withSpan(parent) {
        Kamon.buildSpan("childOperation").ignoreParentFromContext().start()
      }

      val childData = inspect(child)
      childData.context().parentID shouldBe IdentityProvider.NoIdentifier
    }

    "allow overriding the start timestamp for a Span" in {
      val span = Kamon.buildSpan("myOperation").withFrom(Instant.EPOCH.plusMillis(321)).start()
      val spanData = inspect(span)
      spanData.from() shouldBe Instant.EPOCH.plusMillis(321)
    }

    "preserve the same Span and Parent identifier when creating a Span with a remote parent if join-remote-parents-with-same-span-id is enabled" in {
      val previousConfig = Kamon.config()
      Kamon.reconfigure {
        ConfigFactory.parseString("kamon.trace.join-remote-parents-with-same-span-id = yes")
          .withFallback(Kamon.config())
      }

      val remoteParent = Span.Remote(createSpanContext())
      val childData = inspect(Kamon.buildSpan("local").asChildOf(remoteParent).start())

      childData.context().traceID shouldBe remoteParent.context.traceID
      childData.context().parentID shouldBe remoteParent.context.parentID
      childData.context().spanID shouldBe remoteParent.context.spanID

      Kamon.reconfigure(previousConfig)
    }

    "propagate sampling decisions from parent to child spans, if the decision is known" in {
      val sampledRemoteParent = Span.Remote(createSpanContext().copy(samplingDecision = SamplingDecision.Sample))
      val notSampledRemoteParent = Span.Remote(createSpanContext().copy(samplingDecision = SamplingDecision.DoNotSample))

      Kamon.buildSpan("childOfSampled").asChildOf(sampledRemoteParent).start().context()
        .samplingDecision shouldBe(SamplingDecision.Sample)

      Kamon.buildSpan("childOfNotSampled").asChildOf(notSampledRemoteParent).start().context()
        .samplingDecision shouldBe(SamplingDecision.DoNotSample)
    }

    "take a sampling decision if the parent's decision is unknown" in {
      val previousConfig = Kamon.config()
      Kamon.reconfigure {
        ConfigFactory.parseString("kamon.trace.sampler = always")
          .withFallback(Kamon.config())
      }

      val unknownSamplingRemoteParent = Span.Remote(createSpanContext().copy(samplingDecision = SamplingDecision.Unknown))
      Kamon.buildSpan("childOfSampled").asChildOf(unknownSamplingRemoteParent).start().context()
        .samplingDecision shouldBe(SamplingDecision.Sample)

      Kamon.reconfigure(previousConfig)
    }

    "allow a custom sampler" in {
      val previousConfig = Kamon.config()
      Kamon.reconfigure {
        ConfigFactory.parseString("""
          kamon.trace.sampler        = "custom"
          kamon.trace.custom-sampler = "kamon.trace.TestSampler"
        """).withFallback(Kamon.config())
      }

      Kamon.buildSpan(TestSampler.MagicPrefix + "testOp").start().context()
          .samplingDecision shouldBe(SamplingDecision.Sample)

      Kamon.buildSpan("NOT" + TestSampler.MagicPrefix + "testOp").start().context()
          .samplingDecision shouldBe(SamplingDecision.DoNotSample)

      Kamon.reconfigure(previousConfig)
    }

  }

}
