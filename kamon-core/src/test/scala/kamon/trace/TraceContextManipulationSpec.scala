package kamon.trace

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKitBase }
import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpecLike }

class TraceContextManipulationSpec extends TestKitBase with WordSpecLike with Matchers with ImplicitSender {
  implicit lazy val system: ActorSystem = ActorSystem("trace-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  tick-interval = 1 hour
      |  filters = [
      |    {
      |      trace {
      |        includes = [ "*" ]
      |        excludes = [ "non-tracked-trace"]
      |      }
      |    }
      |  ]
      |  precision {
      |    default-histogram-precision {
      |      highest-trackable-value = 3600000000000
      |      significant-value-digits = 2
      |    }
      |
      |    default-min-max-counter-precision {
      |      refresh-interval = 1 second
      |      highest-trackable-value = 999999999
      |      significant-value-digits = 2
      |    }
      |  }
      |}
    """.stripMargin))

  "the TraceRecorder api" should {
    "allow starting a trace within a specified block of code, and only within that block of code" in {
      val createdContext = TraceRecorder.withNewTraceContext("start-context") {
        TraceRecorder.currentContext should not be empty
        TraceRecorder.currentContext
      }

      TraceRecorder.currentContext shouldBe empty
      createdContext.name shouldBe ("start-context")
    }

    "allow starting a trace within a specified block of code, providing a trace-token and only within that block of code" in {
      val createdContext = TraceRecorder.withNewTraceContext("start-context-with-token", Some("token-1")) {
        TraceRecorder.currentContext should not be empty
        TraceRecorder.currentContext
      }

      TraceRecorder.currentContext shouldBe empty
      createdContext.name shouldBe ("start-context-with-token")
      createdContext.token should be("token-1")
    }

    "allow providing a TraceContext and make it available within a block of code" in {
      val createdContext = TraceRecorder.withNewTraceContext("manually-provided-trace-context") { TraceRecorder.currentContext }

      TraceRecorder.currentContext shouldBe empty
      TraceRecorder.withTraceContext(createdContext) {
        TraceRecorder.currentContext should be(createdContext)
      }

      TraceRecorder.currentContext shouldBe empty
    }

    "allow renaming a trace" in {
      val createdContext = TraceRecorder.withNewTraceContext("trace-before-rename") {
        TraceRecorder.rename("renamed-trace")
        TraceRecorder.currentContext
      }

      TraceRecorder.currentContext shouldBe empty
      createdContext.name shouldBe ("renamed-trace")
    }

    "allow creating a segment within a trace" in {
      val createdContext = TraceRecorder.withNewTraceContext("trace-with-segments") {
        val segment = TraceRecorder.currentContext.startSegment("segment-1", "segment-1-category", "segment-library")
        TraceRecorder.currentContext
      }

      TraceRecorder.currentContext shouldBe empty
      createdContext.name shouldBe ("trace-with-segments")
    }

    "allow renaming a segment" in {
      TraceRecorder.withNewTraceContext("trace-with-renamed-segment") {
        val segment = TraceRecorder.currentContext.startSegment("original-segment-name", "segment-label", "segment-library")
        segment.name should be("original-segment-name")

        segment.rename("new-segment-name")
        segment.name should be("new-segment-name")
      }
    }
  }
}
