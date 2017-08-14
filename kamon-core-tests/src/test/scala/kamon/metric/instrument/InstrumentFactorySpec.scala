package kamon.metric.instrument

//import java.time.Duration
//
//import com.typesafe.config.ConfigFactory
//import kamon.metric.Entity
//import org.scalatest.{Matchers, WordSpec}
//
//class InstrumentFactorySpec extends WordSpec with Matchers{
//  val testEntity = Entity("test", "test-category", Map.empty)
//  val customEntity = Entity("test", "custom-category", Map.empty)
//  val baseConfiguration = ConfigFactory.parseString(
//    """
//      |kamon.metric.instrument-factory {
//      |  default-settings {
//      |    histogram {
//      |      lowest-discernible-value = 100
//      |      highest-trackable-value = 5000
//      |      significant-value-digits = 2
//      |    }
//      |
//      |    min-max-counter {
//      |      lowest-discernible-value = 200
//      |      highest-trackable-value = 6000
//      |      significant-value-digits = 3
//      |      sample-interval = 647 millis
//      |    }
//      |  }
//      |
//      |  custom-settings {
//      |
//      |  }
//      |}
//    """.stripMargin
//  )
//
//
//  "the metrics InstrumentFactory" should {
//    "create instruments using the default configuration settings" in {
//      val factory = InstrumentFactory.fromConfig(baseConfiguration)
//      val histogram = factory.buildHistogram(testEntity, "my-histogram")
//      val mmCounter = factory.buildMinMaxCounter(testEntity, "my-mm-counter")
//
//      histogram.dynamicRange.lowestDiscernibleValue shouldBe(100)
//      histogram.dynamicRange.highestTrackableValue shouldBe(5000)
//      histogram.dynamicRange.significantValueDigits shouldBe(2)
//
//      mmCounter.dynamicRange.lowestDiscernibleValue shouldBe(200)
//      mmCounter.dynamicRange.highestTrackableValue shouldBe(6000)
//      mmCounter.dynamicRange.significantValueDigits shouldBe(3)
//      mmCounter.sampleInterval shouldBe(Duration.ofMillis(647))
//    }
//
//    "accept custom settings when building instruments" in {
//      val factory = InstrumentFactory.fromConfig(baseConfiguration)
//      val histogram = factory.buildHistogram(testEntity, "my-histogram", DynamicRange.Loose)
//      val mmCounter = factory.buildMinMaxCounter(testEntity, "my-mm-counter", DynamicRange.Fine, Duration.ofMillis(500))
//
//      histogram.dynamicRange shouldBe(DynamicRange.Loose)
//
//      mmCounter.dynamicRange shouldBe(DynamicRange.Fine)
//      mmCounter.sampleInterval shouldBe(Duration.ofMillis(500))
//    }
//
//    "allow overriding any default and provided settings via the custom-settings configuration key" in {
//      val customConfig = ConfigFactory.parseString(
//        """
//          |kamon.metric.instrument-factory.custom-settings {
//          |   custom-category {
//          |     modified-histogram {
//          |       lowest-discernible-value = 99
//          |       highest-trackable-value = 999
//          |       significant-value-digits = 4
//          |     }
//          |
//          |     modified-mm-counter {
//          |       lowest-discernible-value = 784
//          |       highest-trackable-value = 14785
//          |       significant-value-digits = 1
//          |       sample-interval = 3 seconds
//          |     }
//          |   }
//          |}
//        """.stripMargin
//      ).withFallback(baseConfiguration)
//
//      val factory = InstrumentFactory.fromConfig(customConfig)
//      val defaultHistogram = factory.buildHistogram(customEntity, "default-histogram")
//      val modifiedHistogram = factory.buildHistogram(customEntity, "modified-histogram", DynamicRange.Loose)
//
//      defaultHistogram.dynamicRange.lowestDiscernibleValue shouldBe(100)
//      defaultHistogram.dynamicRange.highestTrackableValue shouldBe(5000)
//      defaultHistogram.dynamicRange.significantValueDigits shouldBe(2)
//
//      modifiedHistogram.dynamicRange.lowestDiscernibleValue shouldBe(99)
//      modifiedHistogram.dynamicRange.highestTrackableValue shouldBe(999)
//      modifiedHistogram.dynamicRange.significantValueDigits shouldBe(4)
//
//
//      val defaultMMCounter = factory.buildMinMaxCounter(customEntity, "default-mm-counter")
//      val modifiedMMCounter = factory.buildMinMaxCounter(customEntity, "modified-mm-counter", DynamicRange.Loose)
//
//      defaultMMCounter.dynamicRange.lowestDiscernibleValue shouldBe(200)
//      defaultMMCounter.dynamicRange.highestTrackableValue shouldBe(6000)
//      defaultMMCounter.dynamicRange.significantValueDigits shouldBe(3)
//      defaultMMCounter.sampleInterval shouldBe(Duration.ofMillis(647))
//
//      modifiedMMCounter.dynamicRange.lowestDiscernibleValue shouldBe(784)
//      modifiedMMCounter.dynamicRange.highestTrackableValue shouldBe(14785)
//      modifiedMMCounter.dynamicRange.significantValueDigits shouldBe(1)
//      modifiedMMCounter.sampleInterval shouldBe(Duration.ofSeconds(3))
//    }
//  }
//}
