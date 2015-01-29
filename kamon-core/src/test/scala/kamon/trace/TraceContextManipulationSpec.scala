package kamon.trace

import com.typesafe.config.ConfigFactory
import kamon.testkit.BaseKamonSpec

class TraceContextManipulationSpec extends BaseKamonSpec("trace-metrics-spec") {
  override lazy val config =
    ConfigFactory.parseString(
      """
        |kamon.metric {
        |  tick-interval = 1 hour
        |
        |  filters {
        |    trace {
        |      includes = [ "*" ]
        |      excludes = [ "non-tracked-trace"]
        |    }
        |  }
        |}
      """.stripMargin)

  "the TraceContext api" should {
    "allow starting a trace within a specified block of code, and only within that block of code" in {
      val createdContext = TraceContext.withContext(newContext("start-context")) {
        TraceContext.currentContext should not be empty
        TraceContext.currentContext
      }

      TraceContext.currentContext shouldBe empty
      createdContext.name shouldBe ("start-context")
    }

    "allow starting a trace within a specified block of code, providing a trace-token and only within that block of code" in {
      val createdContext = TraceContext.withContext(newContext("start-context-with-token", "token-1")) {
        TraceContext.currentContext should not be empty
        TraceContext.currentContext
      }

      TraceContext.currentContext shouldBe empty
      createdContext.name shouldBe ("start-context-with-token")
      createdContext.token should be("token-1")
    }

    "allow providing a TraceContext and make it available within a block of code" in {
      val createdContext = newContext("manually-provided-trace-context")

      TraceContext.currentContext shouldBe empty
      TraceContext.withContext(createdContext) {
        TraceContext.currentContext should be(createdContext)
      }

      TraceContext.currentContext shouldBe empty
    }

    "allow renaming a trace" in {
      val createdContext = TraceContext.withContext(newContext("trace-before-rename")) {
        TraceContext.currentContext.rename("renamed-trace")
        TraceContext.currentContext
      }

      TraceContext.currentContext shouldBe empty
      createdContext.name shouldBe ("renamed-trace")
    }

    "allow creating a segment within a trace" in {
      val createdContext = TraceContext.withContext(newContext("trace-with-segments")) {
        val segment = TraceContext.currentContext.startSegment("segment-1", "segment-1-category", "segment-library")
        TraceContext.currentContext
      }

      TraceContext.currentContext shouldBe empty
      createdContext.name shouldBe ("trace-with-segments")
    }

    "allow renaming a segment" in {
      TraceContext.withContext(newContext("trace-with-renamed-segment")) {
        val segment = TraceContext.currentContext.startSegment("original-segment-name", "segment-label", "segment-library")
        segment.name should be("original-segment-name")

        segment.rename("new-segment-name")
        segment.name should be("new-segment-name")
      }
    }
  }
}
