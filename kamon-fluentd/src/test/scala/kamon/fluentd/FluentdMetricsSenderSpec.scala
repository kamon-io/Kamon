/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package kamon.fluentd

import akka.actor.Props
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric._
import kamon.metric.instrument.{ InstrumentFactory, UnitOfMeasurement }
import kamon.testkit.BaseKamonSpec
import kamon.util.MilliTimestamp
import org.easymock.EasyMock.{ expect ⇒ mockExpect }
import org.fluentd.logger.scala.sender.Sender
import org.scalatest.mock.EasyMockSugar

class FluentdMetricsSenderSpec extends BaseKamonSpec("fluentd-metrics-sender-spec") with EasyMockSugar {
  "FluentdMetricsSender" should {

    "be able to send counter value in single instrument entity" in new MockingFluentLoggerSenderFixture {
      expecting {
        mockExpect(fluentSenderMock.emit(
          "kamon.fluentd.my-app.counter.sample_counter", tickTo / 1000,
          Map(
            "app.name" -> "my-app",
            "category.name" -> "counter",
            "entity.name" -> "sample_counter",
            "unit_of_measurement.name" -> "unknown",
            "unit_of_measurement.label" -> "unknown",
            "metric.name" -> "counter",
            "stats.name" -> "count",
            "value" -> increment,
            "canonical_metric.name" -> "my-app.counter.sample_counter.count",
            "my-app.counter.sample_counter.count" -> increment))).andReturn(true)
        mockExpect(fluentSenderMock.flush())
      }

      whenExecuting(fluentSenderMock) {
        val (entity, testRecorder) = buildSimpleCounter("sample_counter")
        testRecorder.instrument.increment(increment)
        run(Map(entity -> testRecorder.collect(collectionContext)))
        Thread.sleep(100)
      }
    }

    "be able to send histogram in single instrument entity" in new MockingFluentLoggerSenderFixture {
      expecting {
        expectHistgramLog(fluentSenderMock, "my-app", "histogram", "my_histogram")
        mockExpect(fluentSenderMock.flush())
      }

      whenExecuting(fluentSenderMock) {
        val (entity, testRecorder) = buildSimpleHistogram("my_histogram")
        histogramData.foreach(testRecorder.instrument.record(_))
        run(Map(entity -> testRecorder.collect(collectionContext)))
        Thread.sleep(100)
      }
    }

    "be able to send counter in multiple instrument entity" in new MockingFluentLoggerSenderFixture {
      expecting {
        mockExpect(fluentSenderMock.emit(
          "kamon.fluentd.my-app.sample_category.dummy_entity.my_counter", tickTo / 1000,
          Map(
            "app.name" -> "my-app",
            "category.name" -> "sample_category",
            "entity.name" -> "dummy_entity",
            "metric.name" -> "my_counter",
            "stats.name" -> "count",
            "value" -> increment,
            "unit_of_measurement.name" -> "unknown",
            "unit_of_measurement.label" -> "unknown",
            "canonical_metric.name" -> "my-app.sample_category.dummy_entity.my_counter.count",
            "my-app.sample_category.dummy_entity.my_counter.count" -> increment,
            "tags.tagName" -> "tagValue"))).andReturn(true)
        mockExpect(fluentSenderMock.flush())
      }

      whenExecuting(fluentSenderMock) {
        val (entity, testRecorder) = buildRecorder("dummy_entity", Map("tagName" -> "tagValue"))
        testRecorder.myCounter.increment(increment)
        run(Map(entity -> testRecorder.collect(collectionContext)))
        Thread.sleep(100)
      }
    }

    "be able to send histogram in multiple instrument entity" in new MockingFluentLoggerSenderFixture {
      expecting {
        expectHistgramLog(fluentSenderMock, "my-app", "sample_category", "dummy_entity", "my_histogram")
        mockExpect(fluentSenderMock.flush())
      }

      whenExecuting(fluentSenderMock) {
        val (entity, testRecorder) = buildRecorder("dummy_entity")
        histogramData.foreach(testRecorder.myHistogram.record(_))
        run(Map(entity -> testRecorder.collect(collectionContext)))
        Thread.sleep(100)
      }
    }

  }

  trait MockingFluentLoggerSenderFixture {
    val fluentSenderMock: Sender = mock[Sender]

    val tickFrom = 100000L
    val tickTo = 150000L
    val histogramData = (1 to 1000).toList
    val increment: Long = 200L

    def expectHistgramLog(mock: Sender, appName: String, categoryName: String,
      entityName: String, instrumentName: String = "histogram") = {
      val expectedAttr = Map(
        "app.name" -> appName,
        "category.name" -> s"${categoryName}",
        "entity.name" -> s"${entityName}",
        "metric.name" -> s"${instrumentName}",
        "unit_of_measurement.label" -> "unknown",
        "unit_of_measurement.name" -> "unknown")
      val expectedCanonicalMetricName = if (categoryName == "histogram")
        s"${appName}.${categoryName}.${entityName}"
      else
        s"${appName}.${categoryName}.${entityName}.${instrumentName}"

      mockExpect(mock.emit(
        s"kamon.fluentd.${expectedCanonicalMetricName}", tickTo / 1000,
        expectedAttr ++ Map(
          "stats.name" -> "count",
          "value" -> 1000,
          "canonical_metric.name" -> s"${expectedCanonicalMetricName}.count",
          s"${expectedCanonicalMetricName}.count" -> 1000))).andReturn(true)

      mockExpect(mock.emit(
        s"kamon.fluentd.${expectedCanonicalMetricName}", tickTo / 1000,
        expectedAttr ++ Map(
          "stats.name" -> "min",
          "value" -> 1,
          "canonical_metric.name" -> s"${expectedCanonicalMetricName}.min",
          s"${expectedCanonicalMetricName}.min" -> 1))).andReturn(true)

      mockExpect(mock.emit(
        s"kamon.fluentd.${expectedCanonicalMetricName}", tickTo / 1000,
        expectedAttr ++ Map(
          "stats.name" -> "max",
          "value" -> 1000,
          "canonical_metric.name" -> s"${expectedCanonicalMetricName}.max",
          s"${expectedCanonicalMetricName}.max" -> 1000))).andReturn(true)

      mockExpect(mock.emit(
        s"kamon.fluentd.${expectedCanonicalMetricName}", tickTo / 1000,
        expectedAttr ++ Map(
          "stats.name" -> "average",
          "value" -> 499.0,
          "canonical_metric.name" -> s"${expectedCanonicalMetricName}.average",
          s"${expectedCanonicalMetricName}.average" -> 499.0))).andReturn(true)

      mockExpect(mock.emit(
        s"kamon.fluentd.${expectedCanonicalMetricName}", tickTo / 1000,
        expectedAttr ++ Map(
          "stats.name" -> "percentiles.50",
          "value" -> 500,
          "canonical_metric.name" -> s"${expectedCanonicalMetricName}.percentiles.50",
          s"${expectedCanonicalMetricName}.percentiles.50" -> 500))).andReturn(true)

      mockExpect(mock.emit(
        s"kamon.fluentd.${expectedCanonicalMetricName}", tickTo / 1000,
        expectedAttr ++ Map(
          "stats.name" -> "percentiles.90",
          "value" -> 900,
          "canonical_metric.name" -> s"${expectedCanonicalMetricName}.percentiles.90",
          s"${expectedCanonicalMetricName}.percentiles.90" -> 900))).andReturn(true)

      mockExpect(mock.emit(
        s"kamon.fluentd.${expectedCanonicalMetricName}", tickTo / 1000,
        expectedAttr ++ Map(
          "stats.name" -> "percentiles.95",
          "value" -> 948,
          "canonical_metric.name" -> s"${expectedCanonicalMetricName}.percentiles.95",
          s"${expectedCanonicalMetricName}.percentiles.95" -> 948))).andReturn(true)

      mockExpect(mock.emit(
        s"kamon.fluentd.${expectedCanonicalMetricName}", tickTo / 1000,
        expectedAttr ++ Map(
          "stats.name" -> "percentiles.99",
          "value" -> 988,
          "canonical_metric.name" -> s"${expectedCanonicalMetricName}.percentiles.99",
          s"${expectedCanonicalMetricName}.percentiles.99" -> 988))).andReturn(true)

      mockExpect(mock.emit(
        s"kamon.fluentd.${expectedCanonicalMetricName}", tickTo / 1000,
        expectedAttr ++ Map(
          "stats.name" -> "percentiles.99_9",
          "value" -> 1000,
          "canonical_metric.name" -> s"${expectedCanonicalMetricName}.percentiles.99_9",
          s"${expectedCanonicalMetricName}.percentiles.99_9" -> 1000))).andReturn(true)
    }

    def buildRecorder(name: String, tags: Map[String, String] = Map.empty): (Entity, TestEntityRecorder) = {
      val entity = Entity(name, TestEntityRecorder.category, tags)
      val recorder = Kamon.metrics.entity(TestEntityRecorder, entity)
      (entity, recorder)
    }

    def buildSimpleCounter(name: String, tags: Map[String, String] = Map.empty): (Entity, CounterRecorder) = {
      val entity = Entity(name, SingleInstrumentEntityRecorder.Counter, tags)
      val counter = Kamon.metrics.counter(name, tags)
      val recorder = CounterRecorder(CounterKey("counter", UnitOfMeasurement.Unknown), counter)
      (entity, recorder)
    }

    def buildSimpleHistogram(name: String, tags: Map[String, String] = Map.empty): (Entity, HistogramRecorder) = {
      val entity = Entity(name, SingleInstrumentEntityRecorder.Histogram, tags)
      val histogram = Kamon.metrics.histogram(name, tags)
      val recorder = HistogramRecorder(CounterKey("histogram", UnitOfMeasurement.Unknown), histogram)
      (entity, recorder)
    }

    def run(metrics: Map[Entity, EntitySnapshot]) = {
      val histoGramStatConfig = new HistogramStatsConfig(List("*"), List(50.0, 90.0, 95.0, 99.0, 99.9))
      val metricsSender = system.actorOf(Props(
        new FluentdMetricsSender("kamon.fluentd", "localhost", 24224, histoGramStatConfig) {
          override def sender(host: String, port: Int): Sender = fluentSenderMock
        }))
      val fakeSnapshot = TickMetricSnapshot(new MilliTimestamp(tickFrom), new MilliTimestamp(tickTo), metrics)
      metricsSender ! fakeSnapshot
    }
  }

}

class TestEntityRecorder(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val myHistogram = histogram("my_histogram")
  val myCounter = counter("my_counter")
}

object TestEntityRecorder extends EntityRecorderFactory[TestEntityRecorder] {
  def category: String = "sample_category"

  def createRecorder(instrumentFactory: InstrumentFactory): TestEntityRecorder = new TestEntityRecorder(instrumentFactory)
}

// Sender.emit("kamon.fluentd.my-app.sample_category.dummy_entity.my_counter", 150, Map(stats.name -> count, unit_of_measurement.name -> unknown, my-app.sample_category.dummy_entity.my_counter.count -> 200, entity.name -> dummy_entity, category.name -> sample_category, canonical_metric.name -> my-app.sample_category.dummy_entity.my_counter.count, app.name -> my-app, unit_of_measurement.label -> unknown, tags.tagName -> tagValue, metric.name -> my_counter, value -> 200)):
// Sender.emit("kamon.fluentd.my-app.sample_category.dummy_entity.my_counter", 150, Map(instrument.name -> my_counter, unit_of_measurement.name -> unknown, my-app.sample_category.dummy_entity.my_counter.count -> 200, entity.name -> dummy_entity, category.name -> sample_category, canonical_metric.name -> my-app.sample_category.dummy_entity.my_counter.count, app.name -> my-app, unit_of_measurement.label -> unknown, tags.tagName -> tagValue, metric.name -> count, value -> 200)): expected: 1, actual: 0
