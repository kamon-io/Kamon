/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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
      val createdContext = Tracer.withContext(newContext("start-context")) {
        Tracer.currentContext should not be empty
        Tracer.currentContext
      }

      Tracer.currentContext shouldBe empty
      createdContext.name shouldBe ("start-context")
    }

    "allow starting a trace within a specified block of code, providing a trace-token and only within that block of code" in {
      val createdContext = Tracer.withContext(newContext("start-context-with-token", "token-1")) {
        Tracer.currentContext should not be empty
        Tracer.currentContext
      }

      Tracer.currentContext shouldBe empty
      createdContext.name shouldBe ("start-context-with-token")
      createdContext.token should be("token-1")
    }

    "allow providing a TraceContext and make it available within a block of code" in {
      val createdContext = newContext("manually-provided-trace-context")

      Tracer.currentContext shouldBe empty
      Tracer.withContext(createdContext) {
        Tracer.currentContext should be(createdContext)
      }

      Tracer.currentContext shouldBe empty
    }

    "allow renaming a trace" in {
      val createdContext = Tracer.withContext(newContext("trace-before-rename")) {
        Tracer.currentContext.rename("renamed-trace")
        Tracer.currentContext
      }

      Tracer.currentContext shouldBe empty
      createdContext.name shouldBe ("renamed-trace")
    }

    "allow creating a segment within a trace" in {
      val createdContext = Tracer.withContext(newContext("trace-with-segments")) {
        val segment = Tracer.currentContext.startSegment("segment-1", "segment-1-category", "segment-library")
        Tracer.currentContext
      }

      Tracer.currentContext shouldBe empty
      createdContext.name shouldBe ("trace-with-segments")
    }

    "allow renaming a segment" in {
      Tracer.withContext(newContext("trace-with-renamed-segment")) {
        val segment = Tracer.currentContext.startSegment("original-segment-name", "segment-label", "segment-library")
        segment.name should be("original-segment-name")

        segment.rename("new-segment-name")
        segment.name should be("new-segment-name")
      }
    }
  }
}
