package kamon.instrumentation.jdbc

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import kamon.{ClassLoading, Kamon}
import kamon.instrumentation.jdbc.utils.LoggingSupport

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.duration.Duration

object JdbcInstrumentation extends LoggingSupport {

  @volatile private var _settings = readSettings(Kamon.config())
  Kamon.onReconfigure(newConfig => _settings = readSettings(newConfig))

  private[jdbc] def onStatementFinish(statement: String, elapsedTimeNanos: Long): Unit = {
    if(elapsedTimeNanos >= _settings.slowStatementThresholdNanos)
      _settings.slowStatementProcessors.foreach(_.process(statement, elapsedTimeNanos, _settings.slowStatementThresholdNanos))
  }

  private[jdbc] def onStatementFailure(statement: String, error: Throwable): Unit = {
    _settings.failedStatementProcessors.foreach(_.process(statement, error))
  }


  /**
    * Callback for notifications of statements taking longer than "kamon.instrumentation.jdbc.statements.slow.threshold"
    * to execute.
    */
  trait SlowStatementProcessor {
    def process(statement: String, elapsedTimeNanos: Long, slowThresholdNanos: Long): Unit
  }

  /**
    * Callback for notifications on errors thrown while executing statements.
    */
  trait FailedStatementProcessor {
    def process(sql: String, ex: Throwable): Unit
  }


  object LoggingProcessors {

    final class WarnOnSlowStatement extends SlowStatementProcessor with LoggingSupport {
      override def process(statement: String, elapsedTimeNanos: Long, slowThresholdNanos: Long): Unit = {
        val threshold = Duration.create(slowThresholdNanos, TimeUnit.NANOSECONDS)
        val statementDuration = Duration.create(elapsedTimeNanos, TimeUnit.NANOSECONDS)

        logWarn(s"Query execution exceeded the [${threshold}] threshold and lasted [${statementDuration}]. The query was: [$statement]")
      }
    }

    final class ErrorOnFailedStatement extends FailedStatementProcessor with LoggingSupport {

      override def process(sql: String, ex: Throwable): Unit =
        logError("Statement [{}] failed to execute", ex)
    }
  }


  private case class Settings (
    slowStatementThresholdNanos: Long,
    slowStatementProcessors: List[SlowStatementProcessor],
    failedStatementProcessors: List[FailedStatementProcessor]
  )

  private def readSettings(config: Config): Settings = {
    val jdbcConfig = config.getConfig("kamon.instrumentation.jdbc")
    val slowStatementProcessors = jdbcConfig.getStringList("statements.slow.processors").asScala
      .map(fqcn => ClassLoading.createInstance[SlowStatementProcessor](fqcn))
      .toList

    val failedStatementProcessors = jdbcConfig.getStringList("statements.failed.processors").asScala
      .map(fqcn => ClassLoading.createInstance[FailedStatementProcessor](fqcn))
      .toList

    Settings (
      slowStatementThresholdNanos = jdbcConfig.getDuration("statements.slow.threshold").toNanos(),
      slowStatementProcessors,
      failedStatementProcessors
    )
  }
}
