package kamon.logback

import ch.qos.logback.classic.{Level, LoggerContext}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import kamon.Kamon
import kamon.logback.LogbackEntriesCounterAppender._
import kamon.logback.util.LogbackConfigurator
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar._

class LogbackEntriesCounterAppenderSpec extends WordSpec with Matchers with Eventually {

  "LogbackEntriesCounterAppender" when {
    "a event is logged" should {
      "count it split by event level" in {
        implicit val ctx: LoggerContext = context
        implicit val appender: Appender[ILoggingEvent] = new LogbackEntriesCounterAppender

        val configurator = new LogbackConfigurator(ctx)
        configurator.appender("entriesCounter", appender)

        val reporter = new KamonMemoryMetricReporter
        Kamon.addReporter(reporter)

        logMany(5, Level.ERROR)
        logMany(2, Level.INFO)
        logMany(1, Level.WARN)
        logMany(9, Level.DEBUG)
        logMany(2, Level.ERROR)
        logMany(1, Level.DEBUG)

        eventually(timeout(3 seconds)) {
          reporter.counters.size should be > 0
          reporter.countersTotalByTag(CounterName, LevelTagName) shouldBe
            Map("ERROR" → 7, "INFO" → 2, "WARN" → 1, "DEBUG" → 10)
        }
      }
    }
  }

}
