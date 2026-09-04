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
import kamon.testkit.{InitAndStopKamonAfterAll, Reconfigure, SpanInspection}
import kamon.trace.Identifier.Factory.EightBytesIdentifier
import kamon.trace.Span.Position
import kamon.trace.Trace.SamplingDecision
import kamon.trace.Hooks.{PreFinish, PreStart}
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TracerSpec extends AnyWordSpec with Matchers with SpanInspection.Syntax with OptionValues
    with InitAndStopKamonAfterAll {

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
        .tagMetrics("metric-tag", "value")
        .tagMetrics("metric-tag", "value")
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
      val child = Kamon.runWithSpan(parent) {
        Kamon.spanBuilder("childOperation").asChildOf(parent).start()
      }

      child.parentId shouldBe parent.id
    }

    "ignore the span from the current context as parent if explicitly requested" in {
      val parent = Kamon.spanBuilder("myOperation").start()
      val child = Kamon.runWithSpan(parent) {
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
        .samplingDecision shouldBe (SamplingDecision.Sample)

      Kamon.spanBuilder("childOfNotSampled").asChildOf(notSampledRemoteParent).start().trace
        .samplingDecision shouldBe (SamplingDecision.DoNotSample)
    }

    "take a sampling decision if the parent's decision is unknown" in {
      Reconfigure.sampleAlways()

      val unknownSamplingRemoteParent = remoteSpan(SamplingDecision.Unknown)
      Kamon.spanBuilder("childOfSampled").asChildOf(unknownSamplingRemoteParent).start().trace
        .samplingDecision shouldBe (SamplingDecision.Sample)

      Reconfigure.reset()
    }

    "never sample ignored operations" in {
      Reconfigure.sampleAlways()

      Kamon.spanBuilder("/ready").start().trace.samplingDecision shouldBe (SamplingDecision.DoNotSample)
      Kamon.spanBuilder("/status").start().trace.samplingDecision shouldBe (SamplingDecision.DoNotSample)
      Kamon.spanBuilder("/other").start().trace.samplingDecision shouldBe (SamplingDecision.Sample)

      Reconfigure.reset()
    }

    "figure out the position of a Span in its trace" in {
      Kamon.spanBuilder("root").start().position shouldBe Position.Root
      Kamon.spanBuilder("localRoot").asChildOf(remoteSpan()).start().position shouldBe Position.LocalRoot
    }

    "ignore sampling decision suggestions when the parent Span's trace has a decision already" in {
      val sampledParent = remoteSpan(SamplingDecision.Sample)
      val notSampledParent = remoteSpan(SamplingDecision.DoNotSample)

      Kamon.spanBuilder("suggestions")
        .asChildOf(sampledParent)
        .samplingDecision(SamplingDecision.Unknown)
        .start()
        .trace.samplingDecision shouldBe SamplingDecision.Sample

      Kamon.spanBuilder("suggestions")
        .asChildOf(notSampledParent)
        .samplingDecision(SamplingDecision.Unknown)
        .start()
        .trace.samplingDecision shouldBe SamplingDecision.DoNotSample
    }

    "use sampling decision suggestions when there is no parent" in {
      Kamon.spanBuilder("suggestions")
        .samplingDecision(SamplingDecision.Unknown)
        .start()
        .trace.samplingDecision shouldBe SamplingDecision.Unknown

      Kamon.spanBuilder("suggestions")
        .samplingDecision(SamplingDecision.Sample)
        .start()
        .trace.samplingDecision shouldBe SamplingDecision.Sample

      Kamon.spanBuilder("suggestions")
        .samplingDecision(SamplingDecision.DoNotSample)
        .start()
        .trace.samplingDecision shouldBe SamplingDecision.DoNotSample
    }

    "use sampling decision suggestions when the parent has an unknown sampling decision" in {
      Kamon.spanBuilder("suggestions")
        .asChildOf(remoteSpan(SamplingDecision.Unknown))
        .samplingDecision(SamplingDecision.Sample)
        .start()
        .trace.samplingDecision shouldBe SamplingDecision.Sample

      Kamon.spanBuilder("suggestions")
        .asChildOf(remoteSpan(SamplingDecision.Unknown))
        .samplingDecision(SamplingDecision.DoNotSample)
        .start()
        .trace.samplingDecision shouldBe SamplingDecision.DoNotSample

      Kamon.spanBuilder("suggestions")
        .asChildOf(remoteSpan(SamplingDecision.Unknown))
        .samplingDecision(SamplingDecision.Unknown)
        .start()
        .trace.samplingDecision shouldBe SamplingDecision.Unknown
    }

    "not let Spans with remote parents remain with a Unknown sampling decision, even without suggestions" in {
      Kamon.spanBuilder("suggestions")
        .asChildOf(remoteSpan(SamplingDecision.Unknown))
        .start()
        .trace.samplingDecision should not be (SamplingDecision.Unknown)
    }

    "not change a Spans sampling decision if they were created with Sample or DoNotSample decisions sampling decision" in {
      val sampledSpan = Kamon.spanBuilder("suggestions")
        .samplingDecision(SamplingDecision.Sample)
        .start()

      val notSampledSpan = Kamon.spanBuilder("suggestions")
        .samplingDecision(SamplingDecision.DoNotSample)
        .start()

      sampledSpan.trace.samplingDecision shouldBe SamplingDecision.Sample
      sampledSpan.takeSamplingDecision()
      sampledSpan.trace.samplingDecision shouldBe SamplingDecision.Sample

      notSampledSpan.trace.samplingDecision shouldBe SamplingDecision.DoNotSample
      notSampledSpan.takeSamplingDecision()
      notSampledSpan.trace.samplingDecision shouldBe SamplingDecision.DoNotSample
    }

    "allow Spans to take a sampling decision if they were created with Unknown sampling decision" in {
      val span = Kamon.spanBuilder("suggestions")
        .samplingDecision(SamplingDecision.Unknown)
        .start()

      span.trace.samplingDecision shouldBe SamplingDecision.Unknown
      span.takeSamplingDecision()
      span.trace.samplingDecision should not be SamplingDecision.Unknown
    }

    "ensure that all local Spans share the exact same Trace instance" in {
      val remoteParent = remoteSpan()
      val parent = Kamon.spanBuilder("parent").asChildOf(remoteParent).start()
      val child = Kamon.spanBuilder("child").asChildOf(parent).start()
      val grandChild = Kamon.spanBuilder("grandChild").asChildOf(child).start()

      parent.trace shouldBe theSameInstanceAs(remoteParent.trace)
      child.trace shouldBe theSameInstanceAs(remoteParent.trace)
      grandChild.trace shouldBe theSameInstanceAs(remoteParent.trace)
    }

    "allow explicitly dropping traces" in {
      val span = Kamon.spanBuilder("suggestions")
        .samplingDecision(SamplingDecision.Sample)
        .start()

      span.trace.samplingDecision shouldBe SamplingDecision.Sample
      span.trace.drop()
      span.trace.samplingDecision shouldBe SamplingDecision.DoNotSample
    }

    "allow explicitly keep traces" in {
      val span = Kamon.spanBuilder("suggestions")
        .samplingDecision(SamplingDecision.DoNotSample)
        .start()

      span.trace.samplingDecision shouldBe SamplingDecision.DoNotSample
      span.trace.keep()
      span.trace.samplingDecision shouldBe SamplingDecision.Sample
    }

    "apply pre-start hooks to all Spans" in {
      val span = Kamon.runWithContextEntry(PreStart.Key, PreStart.updateOperationName("customName")) {
        Kamon.spanBuilder("defaultOperationName").start()
      }

      span.operationName() shouldBe "customName"
    }

    "apply pre-finish hooks to all Spans" in {
      val span = Kamon.spanBuilder("defaultOperationName").start()
      Kamon.runWithContextEntry(PreFinish.Key, PreFinish.updateOperationName("customName")) {
        span.finish()
      }

      span.operationName() shouldBe "customName"
    }

    "collect exception information for failed Spans" in {
      val byMessage = Kamon.spanBuilder("o1").start()
        .fail("byMessage")
      val byException = Kamon.spanBuilder("o2").start()
        .fail(new RuntimeException("byException"))
      val byMessageAndException = Kamon.spanBuilder("o3").start()
        .fail("byMessageAndException", new RuntimeException("byException"))

      Reconfigure.applyConfig("kamon.trace.include-error-stacktrace=false")
      val byExceptionStacktraceDisabled = Kamon.spanBuilder("o4").start()
        .fail(new RuntimeException("byExceptionStacktraceDisabled"))

      Reconfigure.applyConfig("kamon.trace.include-error-type=false")
      val byExceptionTypeDisabled = Kamon.spanBuilder("o5").start()
        .fail(new RuntimeException("byExceptionTypeEnabled"))

      byMessage.metricTags().get(plainBoolean("error")) shouldBe true
      byMessage.spanTags().get(plain("error.message")) shouldBe "byMessage"
      byMessage.spanTags().get(option("error.stacktrace")) shouldBe None
      byMessage.spanTags().get(option("error.type")) shouldBe None

      byException.metricTags().get(plainBoolean("error")) shouldBe true
      byException.spanTags().get(plain("error.message")) shouldBe "byException"
      byException.spanTags().get(option("error.stacktrace")) shouldBe defined
      byException.spanTags().get(option("error.type")) shouldBe Some("java.lang.RuntimeException")

      byMessageAndException.metricTags().get(plainBoolean("error")) shouldBe true
      byMessageAndException.spanTags().get(plain("error.message")) shouldBe "byMessageAndException"
      byMessageAndException.spanTags().get(option("error.stacktrace")) shouldBe defined
      byMessageAndException.spanTags().get(option("error.type")) shouldBe Some("java.lang.RuntimeException")

      byExceptionStacktraceDisabled.metricTags().get(plainBoolean("error")) shouldBe true
      byExceptionStacktraceDisabled.spanTags().get(plain("error.message")) shouldBe "byExceptionStacktraceDisabled"
      byExceptionStacktraceDisabled.spanTags().get(option("error.stacktrace")) shouldBe None
      byExceptionStacktraceDisabled.spanTags().get(option("error.type")) shouldBe Some("java.lang.RuntimeException")

      byExceptionTypeDisabled.metricTags().get(plainBoolean("error")) shouldBe true
      byExceptionTypeDisabled.spanTags().get(plain("error.message")) shouldBe "byExceptionTypeEnabled"
      byExceptionTypeDisabled.spanTags().get(option("error.stacktrace")) shouldBe None
      byExceptionTypeDisabled.spanTags().get(option("error.type")) shouldBe None
    }
  }

  private def remoteSpan(samplingDecision: SamplingDecision = SamplingDecision.Sample): Span.Remote =
    Span.Remote(
      EightBytesIdentifier.generate(),
      EightBytesIdentifier.generate(),
      Trace(EightBytesIdentifier.generate(), samplingDecision)
    )

}
