package kamon.trace

import kamon.Kamon
import kamon.trace.Trace.SamplingDecision
import org.scalactic.TimesOnInt.convertIntToRepeater
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.concurrent.duration._

class SamplerSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  "sampling on the Kamon tracer" when {
    "configured for adaptive sampling" should {
      "spread the throughput across operations when the overall throughput doesn't exceed the global limit" in {
        implicit val sampler = adaptiveSampler()

        simulate(10 minutes) {
          3 times {
            decide("op4")
            decide("op5")
            decide("op6")
          }
        }

        // Each operations should get 180 traces per minute for 10 minutes, which is below the global
        // throughput goal of 600 traces per minute, shared across all operations. Roughly all traces
        // should be sampled.
        sampledTraces("op4") shouldBe 1800 +- 50
        sampledTraces("op5") shouldBe 1800 +- 50
        sampledTraces("op6") shouldBe 1800 +- 50
      }

      "remove operations with fixed sampled decisions from the balancing algorithm" in {
        implicit val sampler = adaptiveSampler()

        simulate(10 minutes) {
          3 times {
            decide("op4")
            decide("op5")
            decide("op6")
            decide("op10")
            decide("op11")
          }
        }

        // Each operations should get 180 traces per minute for 10 minutes, which is below the global
        // throughput goal of 600 traces per minute, shared across all operations. Roughly all traces
        // should be sampled.
        sampledTraces("op4") shouldBe 1800 +- 50
        sampledTraces("op5") shouldBe 1800 +- 50
        sampledTraces("op6") shouldBe 1800 +- 50
        sampledTraces("op10") shouldBe 0
        sampledTraces("op11") shouldBe 1800
      }

      "spread the throughput across operations and cap them when the overall throughput exceeds the global limit" in {
        implicit val sampler = adaptiveSampler()

        simulate(10 minutes) {
          10 times {
            decide("op4")
            decide("op5")
            decide("op6")
          }
        }

        // Each operations should get 600 traces per minute for 10 minutes, which is above the global
        // throughput goal of 600 traces per minute so each operation should be capped to roughly 200
        // traces per minute.
        sampledTraces("op4") shouldBe 2000 +- 200
        sampledTraces("op5") shouldBe 2000 +- 200
        sampledTraces("op6") shouldBe 2000 +- 200
        totalSampledTraces() shouldBe 6000 +- 600
      }

      "spread the throughput across operations and respect maximum/minimum throughput rules" in {
        implicit val sampler = adaptiveSampler()

        simulate(10 minutes) {
          10 times {
            decide("op4")
            decide("op5")
            decide("op6")
            decide("op9")
          }
        }

        // Each operations should get 600 traces per minute for 10 minutes, which is above the global
        // throughput goal of 600 traces per minute so each operation should be capped to roughly 200
        // traces per minute, except for op9 which is capped to 40 per minute.
        sampledTraces("op4") shouldBe 1830 +- 180
        sampledTraces("op5") shouldBe 1830 +- 180
        sampledTraces("op6") shouldBe 1830 +- 180
        sampledTraces("op9") shouldBe 400 +- 100
        totalSampledTraces() shouldBe 6000 +- 600
      }

      "spread throughput across operations and respect maximum/minimum throughput rules with uneven traffic" in {
        implicit val sampler = adaptiveSampler()

        simulate(10 minutes) {
          5 times {
            decide("op5")
          }

          10 times {
            decide("op6")
            decide("op9")
          }

          20 times {
            decide("op4")
          }
        }

        // Operations are getting 300, 600 and 1200 traces per minute for a total of 2100 per minute, which is above
        // the global throughput goal of 600 traces per minute so each operation should be capped to roughly 200
        // traces per minute, except for op9 which is capped to 40 per minute.
        sampledTraces("op4") shouldBe 1860 +- 186
        sampledTraces("op5") shouldBe 1860 +- 186
        sampledTraces("op6") shouldBe 1860 +- 186
        sampledTraces("op9") shouldBe 400 +- 100
        totalSampledTraces() shouldBe 6000 +- 600
      }

      "spread unused throughput across operations and respect maximum/minimum throughput rules with uneven traffic" in {
        implicit val sampler = adaptiveSampler()

        simulate(10 minutes) {
          decide("op4")

          10 times {
            decide("op5")
            decide("op9")
          }

          20 times {
            decide("op6")
          }
        }

        // Since op4 is getting just 60 requests per minute it leaves some available throughput that could be used by
        // other more active operations like op5 and op6, and the sampler ensure that it happens. It should be sampling
        // all traces for op4, roughly 20 traces for op9 because of its cap and split the remaining throughput between
        // op5 and op6.
        sampledTraces("op4") shouldBe 600 +- 60
        sampledTraces("op5") shouldBe 2500 +- 250
        sampledTraces("op6") shouldBe 2500 +- 250
        sampledTraces("op9") shouldBe 400 +- 100
        totalSampledTraces() shouldBe 6000 +- 600
      }

      "adapt to changes in behavior over time" in {
        implicit val sampler = adaptiveSampler()

        simulate(10 minutes) {
          decide("op4")

          10 times {
            decide("op5")
            decide("op9")
          }

          20 times {
            decide("op6")
          }
        }

        // Since op4 is getting just 60 requests per minute it leaves some available throughput that could be used by
        // other more active operations like op5 and op6, and the sampler ensure that it happens. It should be sampling
        // all traces for op4, roughly 20 traces for op9 because of its cap and split the remaining throughput between
        // op5 and op6.
        sampledTraces("op4") shouldBe 600 +- 60
        sampledTraces("op5") shouldBe 2500 +- 250
        sampledTraces("op6") shouldBe 2500 +- 250
        sampledTraces("op9") shouldBe 400 +- 100
        totalSampledTraces() shouldBe 6000 +- 600
        resetCounters()

        simulate(5 minutes) {
          decide("op4")

          10 times {
            decide("op5")
            decide("op7")
            decide("op8")
            decide("op9")
          }

          20 times {
            decide("op6")
          }
        }

        // During the last 5 minutes we introduced op7 and op8. Op8 has a minimum throughput rule of 100 so it will end
        // up taking a bigger chunk of the throughput and reducing the available throughput for the rest of the
        // operations
        sampledTraces("op4") shouldBe 300 +- 30
        sampledTraces("op5") shouldBe 600 +- 100
        sampledTraces("op6") shouldBe 600 +- 100
        sampledTraces("op7") shouldBe 600 +- 100
        sampledTraces("op8") shouldBe 550 +- 100
        sampledTraces("op9") shouldBe 200 +- 50
        totalSampledTraces() shouldBe 3000 +- 300
        resetCounters()

        simulate(10 minutes) {
          decide("op4")
          decide("op5")
          decide("op6")

          2 times {
            decide("op7")
            decide("op8")
            decide("op9")
          }
        }

        // During the last 5 minutes we reduced throughput from op4, op5 and op7 to 60 per minute and op6, op8 and op9
        // to 120 per minute, for a total of 540 requests per minute. At this point all traces should be captured since
        // there is enough room to get them all, except for op9 which has a cap.
        sampledTraces("op4") shouldBe 600 +- 100
        sampledTraces("op5") shouldBe 600 +- 100
        sampledTraces("op6") shouldBe 600 +- 100
        sampledTraces("op7") shouldBe 1200 +- 120
        sampledTraces("op8") shouldBe 1200 +- 120
        sampledTraces("op9") shouldBe 400 +- 100
        totalSampledTraces() shouldBe 4400 +- 440
        resetCounters()
      }
    }
  }

  private val _decisions = mutable.Map.empty[String, Int]

  // Gets a decision from an adaptive sampler for the provided operation name and stores the number of sampled
  // responses for it.
  def decide(operationName: String)(implicit sampler: AdaptiveSampler): Unit =
    if (sampler.decide(Kamon.spanBuilder(operationName)) == SamplingDecision.Sample) {
      val current = _decisions.get(operationName).getOrElse(0)
      _decisions.put(operationName, current + 1)
    }

  def sampledTraces(operationName: String): Int =
    _decisions.get(operationName).getOrElse(0)

  def totalSampledTraces(): Int =
    _decisions.values.sum

  def resetCounters(): Unit =
    _decisions.clear()

  override protected def beforeEach(): Unit =
    resetCounters()

  def simulate(duration: Duration)(perSecond: => Unit)(implicit sampler: AdaptiveSampler): Unit = {
    duration.toSeconds.toInt.times {
      perSecond
      sampler.adapt()
    }
  }

  def adaptiveSampler(): AdaptiveSampler =
    new AdaptiveSampler()

}
