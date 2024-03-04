package kamon.instrumentation.logback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, LoggerContext}
import ch.qos.logback.core.Appender
import kamon.instrumentation.logback.LogbackMetrics.LogEvents
import kamon.instrumentation.logback.tools.EntriesCounterAppender
import kamon.logback.util.LogbackConfigurator
import kamon.tag.TagSet
import kamon.testkit.InstrumentInspection
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LogbackEntriesCounterAppenderSpec extends AnyWordSpec with Matchers with InstrumentInspection.Syntax
    with Eventually {

  "LogbackEntriesCounterAppender" when {
    "a event is logged" should {
      "count it split by event level" in {
        implicit val ctx: LoggerContext = context
        implicit val appender: Appender[ILoggingEvent] = new EntriesCounterAppender

        val configurator = new LogbackConfigurator(ctx)
        configurator.appender("entriesCounter", appender)

        logMany(5, Level.ERROR)
        logMany(2, Level.INFO)
        logMany(1, Level.WARN)
        logMany(9, Level.DEBUG)
        logMany(2, Level.ERROR)
        logMany(1, Level.DEBUG)

        eventually {
          LogEvents.withTags(level("ERROR")).value(resetState = false) shouldBe 7L
          LogEvents.withTags(level("WARN")).value(resetState = false) shouldBe 1L
          LogEvents.withTags(level("INFO")).value(resetState = false) shouldBe 2L
          LogEvents.withTags(level("DEBUG")).value(resetState = false) shouldBe 10L
        }
      }
    }
  }

  def level(level: String): TagSet =
    TagSet.builder()
      .add("component", "logback")
      .add("level", level)
      .build()

}
