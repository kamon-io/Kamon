/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.jdbc.instrumentation

import java.sql.{PreparedStatement, Statement}
import java.time.temporal.ChronoUnit

import kamon.Kamon
import kamon.Kamon.buildSpan
import kamon.jdbc.instrumentation.StatementInstrumentation.StatementTypes
import kamon.jdbc.{Jdbc, Metrics}
import kamon.trace.SpanCustomizer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{Around, Aspect, DeclareMixin, Pointcut}


@Aspect
class StatementInstrumentation {

  @DeclareMixin("java.sql.Statement+")
  def mixinHasConnectionPoolTrackerToStatement: Mixin.HasConnectionPoolMetrics = Mixin.HasConnectionPoolMetrics()

  /**
   *   Calls to java.sql.Statement+.execute(..)
   */

  @Pointcut("execution(* (java.sql.Statement+ || org.sqlite.jdbc3.JDBC3Statement+).execute(..)) && args(sql) && this(statement)")
  def statementExecuteWithArguments(sql: String, statement: Statement): Unit = {}

  @Pointcut("execution(* (java.sql.PreparedStatement+ || org.sqlite.jdbc3.JDBC3PreparedStatement+).execute()) && this(statement)")
  def statementExecuteWithoutArguments(statement: PreparedStatement): Unit = {}

  @Around("statementExecuteWithArguments(sql, statement)")
  def aroundStatementExecuteWithArguments(pjp: ProceedingJoinPoint, sql: String, statement: Statement): Any =
    track(pjp, statement, sql, StatementTypes.GenericExecute)

  @Around("statementExecuteWithoutArguments(statement)")
  def aroundStatementExecuteWithoutArguments(pjp: ProceedingJoinPoint, statement: PreparedStatement): Any =
    track(pjp, statement, statement.toString, StatementTypes.GenericExecute)

  /**
   *   Calls to java.sql.Statement+.executeQuery(..)
   */

  @Pointcut("execution(* (java.sql.Statement || org.sqlite.jdbc3.JDBC3Statement+).executeQuery(..)) && args(sql) && this(statement)")
  def statementExecuteQueryWithArguments(sql: String, statement: Statement): Unit = {}

  @Pointcut("execution(* (java.sql.PreparedStatement+ || org.sqlite.jdbc3.JDBC3PreparedStatement+).executeQuery()) && this(statement)")
  def statementExecuteQueryWithoutArguments(statement: PreparedStatement): Unit = {}

  @Around("statementExecuteQueryWithArguments(sql, statement)")
  def aroundStatementExecuteQueryWithArguments(pjp: ProceedingJoinPoint, sql: String, statement: Statement): Any =
    track(pjp, statement, sql, StatementTypes.Query)

  @Around("statementExecuteQueryWithoutArguments(statement)")
  def aroundStatementExecuteQueryWithoutArguments(pjp: ProceedingJoinPoint, statement: PreparedStatement): Any =
    track(pjp, statement, statement.toString, StatementTypes.Query)

  /**
   *   Calls to java.sql.Statement+.executeUpdate(..)
   */

  @Pointcut("execution(* (java.sql.Statement+  || org.sqlite.jdbc3.JDBC3Statement+).executeUpdate(..)) && args(sql) && this(statement)")
  def statementExecuteUpdateWithArguments(sql: String, statement: Statement): Unit = {}

  @Pointcut("execution(* (java.sql.PreparedStatement+ || org.sqlite.jdbc3.JDBC3PreparedStatement+).executeUpdate()) && this(statement)")
  def statementExecuteUpdateWithoutArguments(statement: PreparedStatement): Unit = {}

  @Around("statementExecuteUpdateWithArguments(sql, statement)")
  def aroundStatementExecuteUpdateWithArguments(pjp: ProceedingJoinPoint, sql: String, statement: Statement): Any =
    track(pjp, statement, sql, StatementTypes.Update)

  @Around("statementExecuteUpdateWithoutArguments(statement)")
  def aroundStatementExecuteUpdateWithoutArguments(pjp: ProceedingJoinPoint, statement: PreparedStatement): Any =
    track(pjp, statement, statement.toString, StatementTypes.Update)

  /**
   *   Calls to java.sql.Statement+.executeBatch() and java.sql.Statement+.executeLargeBatch()
   */

  @Pointcut("(execution(* (java.sql.Statement+ || org.sqlite.jdbc3.JDBC3Statement+).executeBatch()) || execution(* (java.sql.Statement+ || org.sqlite.jdbc3.JDBC3Statement+).executeLargeBatch()))  && this(statement)")
  def statementExecuteBatch(statement: Statement): Unit = {}

  @Around("statementExecuteBatch(statement)")
  def aroundStatementExecuteBatch(pjp: ProceedingJoinPoint, statement: Statement): Any =
    track(pjp, statement, statement.toString, StatementTypes.Batch)

  def track(pjp: ProceedingJoinPoint, target: Any, sql: String, statementType: String): Any = {
    val poolTags = Option(target.asInstanceOf[Mixin.HasConnectionPoolMetrics].connectionPoolMetrics)
      .map(_.tags)
      .getOrElse(Map.empty[String, String])

    val inFlight = Metrics.Statements.InFlight.refine(poolTags)
    inFlight.increment()

    val startTimestamp = Kamon.clock().instant()
    val span = Kamon.currentContext().get(SpanCustomizer.ContextKey).customize {
      val builder = buildSpan(statementType)
        .withFrom(startTimestamp)
        .withMetricTag("component", "jdbc")
        .withTag("db.statement", sql)

      poolTags.foreach { case (key, value) => builder.withTag(key, value) }
      builder
    }.start()

    try {

      pjp.proceed()

    } catch {
      case t: Throwable =>
        span.addError("error.object", t)
        Jdbc.onStatementError(sql, t)
        throw t

    } finally {
      val endTimestamp = Kamon.clock().instant()
      val elapsedTime = startTimestamp.until(endTimestamp, ChronoUnit.MICROS)
      span.finish(endTimestamp)
      inFlight.decrement()

      Jdbc.onStatementFinish(sql, elapsedTime)
    }
  }
}

object StatementInstrumentation {
  object StatementTypes {
    val Query = "jdbc.query"
    val Update = "jdbc.update"
    val Batch = "jdbc.batch"
    val GenericExecute = "jdbc.execute"
  }
}

