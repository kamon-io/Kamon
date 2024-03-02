/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.jdbc

import com.typesafe.config.Config
import kamon.Kamon
import kamon.instrumentation.jdbc.utils.{LoggingSupport, SqlVisitor}
import kamon.metric.RangeSampler
import kamon.tag.{Lookups, TagSet}
import kamon.trace.Span
import kanela.agent.bootstrap.stack.CallStackDepth
import net.sf.jsqlparser.parser.CCJSqlParserUtil

import java.sql.PreparedStatement
import java.time.Instant
import java.time.temporal.ChronoUnit

object StatementMonitor extends LoggingSupport {

  object StatementTypes {
    val Query = "query"
    val Update = "update"
    val Batch = "batch"
    val GenericExecute = "execute"
  }

  @volatile private var parseSqlOperationName: Boolean = false
  @volatile private var addStatementSQL: Boolean = true
  @volatile private var addPreparedStatementSQL: Boolean = true

  Kamon.onReconfigure(c => updateSettings(c))
  updateSettings(Kamon.config())

  private def updateSettings(config: Config): Unit = {
    val jdbcInstrumentationConfig = config.getConfig("kamon.instrumentation.jdbc")
    parseSqlOperationName = jdbcInstrumentationConfig.getBoolean("parse-sql-for-operation-name")

    jdbcInstrumentationConfig.getString("add-db-statement-as-span-tag") match {
      case "always" =>
        addStatementSQL = true
        addPreparedStatementSQL = true

      case "prepared" =>
        addStatementSQL = false
        addPreparedStatementSQL = true

      case "never" =>
        addStatementSQL = false
        addPreparedStatementSQL = false

      case unrecognizedValue =>
        logger.warn(s"Unrecognized value [${unrecognizedValue}] for the [add-db-statement-as-span-tag] setting. " +
          s"Falling back to [always]")
    }
  }

  def start(statement: Any, sql: String, statementType: String): Option[Invocation] = {
    if (CallStackDepth.incrementFor(statement) == 0) {
      val startTimestamp = Kamon.clock().instant()

      // It could happen that there is no Pool Telemetry on the Pool when fail-fast is enabled and a connection is
      // created while the Pool's constructor is still executing.
      val (inFlightRangeSampler: RangeSampler, databaseTags: DatabaseTags) = statement match {
        case cpt: HasConnectionPoolTelemetry
            if cpt.connectionPoolTelemetry != null && cpt.connectionPoolTelemetry.get() != null =>
          val poolTelemetry = cpt.connectionPoolTelemetry.get()
          (poolTelemetry.instruments.inFlightStatements, poolTelemetry.databaseTags)

        case dbt: HasDatabaseTags if dbt.databaseTags() != null =>
          (JdbcMetrics.InFlightStatements.withTags(dbt.databaseTags().metricTags), dbt.databaseTags())

        case _ =>
          (JdbcMetrics.InFlightStatements.withoutTags(), DatabaseTags(TagSet.Empty, TagSet.Empty))
      }

      val spanName = if (parseSqlOperationName) {
        try {
          val statement = CCJSqlParserUtil.parse(sql)
          val statementVisitor = new SqlVisitor(statementType)
          statement.accept(statementVisitor)
          statementVisitor.operation
        } catch {
          case e: Exception =>
            if (logger.isInfoEnabled) {
              logger.info(s"Could not parse sql to get operation name: ${sql}", e)
            }
            statementType
        }
      } else {
        statementType
      }

      val clientSpan = Kamon.clientSpanBuilder(spanName, "jdbc")

      if (addStatementSQL || (statement.isInstanceOf[PreparedStatement] && addPreparedStatementSQL))
        clientSpan.tag("db.statement", sql)

      databaseTags.spanTags.iterator().foreach(t =>
        clientSpan.tag(t.key, databaseTags.spanTags.get(Lookups.coerce(t.key)))
      )
      databaseTags.metricTags.iterator().foreach(t =>
        clientSpan.tagMetrics(t.key, databaseTags.metricTags.get(Lookups.coerce(t.key)))
      )
      inFlightRangeSampler.increment()

      Some(Invocation(statement, clientSpan.start(startTimestamp), sql, startTimestamp, inFlightRangeSampler))
    } else None
  }

  case class Invocation(statement: Any, span: Span, sql: String, startedAt: Instant, inFlight: RangeSampler) {

    def close(throwable: Throwable): Unit = {
      if (throwable != null) {
        span.fail(throwable)
        JdbcInstrumentation.onStatementFailure(sql, throwable)
      }

      inFlight.decrement()
      val endedAt = Kamon.clock().instant()
      val elapsedTime = startedAt.until(endedAt, ChronoUnit.NANOS)
      span.finish(endedAt)

      JdbcInstrumentation.onStatementFinish(sql, elapsedTime)
      CallStackDepth.resetFor(statement)
    }
  }
}
