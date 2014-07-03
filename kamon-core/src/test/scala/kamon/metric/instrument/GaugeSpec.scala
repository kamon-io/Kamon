package kamon.metric.instrument

import java.util.concurrent.atomic.AtomicLong

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import kamon.metric.{ Scale, CollectionContext }
import org.scalatest.{ Matchers, WordSpecLike }
import scala.concurrent.duration._

class GaugeSpec extends WordSpecLike with Matchers {
  val system = ActorSystem("gauge-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  flush-interval = 1 hour
      |  precision {
      |    default-gauge-precision {
      |      refresh-interval = 100 milliseconds
      |      highest-trackable-value = 999999999
      |      significant-value-digits = 2
      |    }
      |  }
      |}
    """.stripMargin))

  "a Gauge" should {
    "automatically record the current value using the configured refresh-interval" in {
      val numberOfValuesRecorded = new AtomicLong(0)
      val gauge = Gauge.fromDefaultConfig(system) { () ⇒ numberOfValuesRecorded.addAndGet(1) }

      Thread.sleep(1.second.toMillis)
      numberOfValuesRecorded.get() should be(10L +- 1L)
      gauge.cleanup
    }

    "stop automatically recording after a call to cleanup" in {
      val numberOfValuesRecorded = new AtomicLong(0)
      val gauge = Gauge.fromDefaultConfig(system) { () ⇒ numberOfValuesRecorded.addAndGet(1) }

      Thread.sleep(1.second.toMillis)
      gauge.cleanup
      numberOfValuesRecorded.get() should be(10L +- 1L)
      Thread.sleep(1.second.toMillis)
      numberOfValuesRecorded.get() should be(10L +- 1L)
    }

    "produce a Histogram snapshot including all the recorded values" in {
      val numberOfValuesRecorded = new AtomicLong(0)
      val gauge = Gauge.fromDefaultConfig(system) { () ⇒ numberOfValuesRecorded.addAndGet(1) }

      Thread.sleep(1.second.toMillis)
      gauge.cleanup
      val snapshot = gauge.collect(CollectionContext.default)

      snapshot.numberOfMeasurements should be(10L +- 1L)
      snapshot.min should be(1)
      snapshot.max should be(10L +- 1L)
    }

    "not record the current value when doing a collection" in {
      val numberOfValuesRecorded = new AtomicLong(0)
      val gauge = Gauge(Histogram.Precision.Normal, 10000L, Scale.Unit, 1 hour, system)(() ⇒ numberOfValuesRecorded.addAndGet(1))

      val snapshot = gauge.collect(CollectionContext.default)

      snapshot.numberOfMeasurements should be(0)
      numberOfValuesRecorded.get() should be(0)
    }
  }
}
