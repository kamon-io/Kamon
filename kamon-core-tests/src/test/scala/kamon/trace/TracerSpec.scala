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

import kamon.Kamon
import kamon.tag.Lookups._
import kamon.testkit.{Reconfigure, SpanInspection}
import kamon.trace.Identifier.Factory.EightBytesIdentifier
import kamon.trace.Span.Position
import kamon.trace.Trace.SamplingDecision
import org.scalatest.{Matchers, OptionValues, WordSpec}

class TracerSpec extends WordSpec with Matchers with SpanInspection.Syntax with OptionValues {

  "the Kamon tracer" should {
    "construct a minimal Span that only has a operation name and default metric tags" in {
      val span = Kamon.spanBuilder("myOperation").start()

      span.operationName() shouldBe "myOperation"
      span.spanTags() shouldBe empty
      span.metricTags().get(plain("operation")) shouldBe "myOperation"
      span.metricTags().get(plainBoolean("error")) shouldBe false
    }

    "pass the operation name and tags to started Span" in {
      val span = Kamon.spanBuilder("myOperation")
        .tagMetric("metric-tag", "value")
        .tagMetric("metric-tag", "value")
        .tag("hello", "world")
        .tag("kamon", "rulez")
        .tag("number", 123)
        .tag("boolean", true)
        .start()

      span.operationName() shouldBe "myOperation"
      span.metricTags().get(plain("metric-tag")) shouldBe "value"
      span.spanTags().get(plain("hello")) shouldBe "world"
      span.spanTags().get(plain("kamon")) shouldBe "rulez"
      span.spanTags().get(plainLong("number")) shouldBe 123L
      span.spanTags().get(plainBoolean("boolean")) shouldBe true
    }

    "not have any parent Span if there is no Span in the current context and no parent was explicitly given" in {
      val span = Kamon.spanBuilder("myOperation").start()
      span.parentId shouldBe Identifier.Empty
    }

    "automatically take the Span from the current Context as parent" in {
      val parent = Kamon.spanBuilder("myOperation").start()
      val child = Kamon.withSpan(parent) {
        Kamon.spanBuilder("childOperation").asChildOf(parent).start()
      }

      child.parentId shouldBe parent.id
    }

    "ignore the span from the current context as parent if explicitly requested" in {
      val parent = Kamon.spanBuilder("myOperation").start()
      val child = Kamon.withSpan(parent) {
        Kamon.spanBuilder("childOperation").ignoreParentFromContext().start()
      }

      child.parentId shouldBe Identifier.Empty
    }

    "allow providing a custom start timestamp for a Span" in {
      val span = Kamon.spanBuilder("myOperation").start(Instant.EPOCH.plusMillis(321)).toFinished()
      span.from shouldBe Instant.EPOCH.plusMillis(321)
    }

    "preserve the same Span and Parent identifier when creating a server Span with a remote parent if join-remote-parents-with-same-span-id is enabled" in {
      Reconfigure.enableJoiningRemoteParentWithSameId()

      val remoteParent = remoteSpan(SamplingDecision.Sample)
      val child = Kamon.spanBuilder("local").asChildOf(remoteParent).kind(Span.Kind.Server).start()

      child.id shouldBe remoteParent.id
      child.parentId shouldBe remoteParent.parentId
      child.trace.id shouldBe remoteParent.trace.id

      Reconfigure.reset()
    }

    "propagate sampling decisions from parent to child spans, if the decision is known" in {
      val sampledRemoteParent = remoteSpan(SamplingDecision.Sample)
      val notSampledRemoteParent = remoteSpan(SamplingDecision.DoNotSample)

      Kamon.spanBuilder("childOfSampled").asChildOf(sampledRemoteParent).start().trace
        .samplingDecision shouldBe(SamplingDecision.Sample)

      Kamon.spanBuilder("childOfNotSampled").asChildOf(notSampledRemoteParent).start().trace
        .samplingDecision shouldBe(SamplingDecision.DoNotSample)
    }

    "take a sampling decision if the parent's decision is unknown" in {
      Reconfigure.sampleAlways()

      val unknownSamplingRemoteParent = remoteSpan(SamplingDecision.Unknown)
      Kamon.spanBuilder("childOfSampled").asChildOf(unknownSamplingRemoteParent).start().trace
        .samplingDecision shouldBe(SamplingDecision.Sample)

      Reconfigure.reset()
    }

    "figure out the position of a Span in its trace" in {
      Kamon.spanBuilder("root").start().position shouldBe Position.Root
      Kamon.spanBuilder("localRoot").asChildOf(remoteSpan()).start().position shouldBe Position.LocalRoot
    }
  }

  private def remoteSpan(samplingDecision: SamplingDecision = SamplingDecision.Sample): Span.Remote =
    new Span.Remote(EightBytesIdentifier.generate(), EightBytesIdentifier.generate(), Trace(EightBytesIdentifier.generate(), samplingDecision))

}
