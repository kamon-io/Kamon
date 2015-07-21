package kamon.trace

import kamon.testkit.BaseKamonSpec
import kamon.util.NanoInterval

class SamplerSpec extends BaseKamonSpec("sampler-spec") {

  "the Sampler" should {
    "work as intended" when {
      "using all mode" in {
        val sampler = SampleAll

        sampler.shouldTrace should be(true)

        sampler.shouldReport(NanoInterval.default) should be(true)
      }

      "using random mode" in {
        val sampler = new RandomSampler(100)

        sampler.shouldTrace should be(true)
        sampler.shouldTrace should be(true)

        sampler.shouldReport(NanoInterval.default) should be(true)
      }

      "using ordered mode" in {
        var sampler = new OrderedSampler(1)

        sampler.shouldTrace should be(true)
        sampler.shouldTrace should be(true)
        sampler.shouldTrace should be(true)
        sampler.shouldTrace should be(true)
        sampler.shouldTrace should be(true)
        sampler.shouldTrace should be(true)

        sampler = new OrderedSampler(2)

        sampler.shouldTrace should be(false)
        sampler.shouldTrace should be(true)
        sampler.shouldTrace should be(false)
        sampler.shouldTrace should be(true)
        sampler.shouldTrace should be(false)
        sampler.shouldTrace should be(true)

        sampler.shouldReport(NanoInterval.default) should be(true)
      }

      "using threshold mode" in {
        val sampler = new ThresholdSampler(new NanoInterval(10000000L))

        sampler.shouldTrace should be(true)

        sampler.shouldReport(new NanoInterval(5000000L)) should be(false)
        sampler.shouldReport(new NanoInterval(10000000L)) should be(true)
        sampler.shouldReport(new NanoInterval(15000000L)) should be(true)
        sampler.shouldReport(new NanoInterval(0L)) should be(false)
      }

      "using clock mode" in {
        val sampler = new ClockSampler(new NanoInterval(10000000L))

        sampler.shouldTrace should be(true)
        sampler.shouldTrace should be(false)
        Thread.sleep(1L)
        sampler.shouldTrace should be(false)
        Thread.sleep(10L)
        sampler.shouldTrace should be(true)
        sampler.shouldTrace should be(false)

        sampler.shouldReport(NanoInterval.default) should be(true)
      }
    }
  }

}
