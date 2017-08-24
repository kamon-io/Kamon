/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import kamon.Kamon
import kamon.Kamon.buildSpan
import kamon.jdbc.{JdbcExtension, Metrics}
import kamon.jdbc.instrumentation.StatementInstrumentation.StatementTypes
import kamon.trace.SpanCustomizer
import kamon.util.Clock
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{Around, Aspect, DeclareMixin, Pointcut}


@Aspect
class StatementInstrumentation {

  @DeclareMixin("java.sql.Statement+")
  def mixinHasConnectionPoolTrackerToStatement: Mixin.HasConnectionPoolMetrics = Mixin.HasConnectionPoolMetrics()

  /**
   *   Calls to java.sql.Statement+.execute(..)
   */

  @Pointcut("execution(* java.sql.Statement+.execute(..)) && args(sql) && this(statement)")
  def statementExecuteWithArguments(sql: String, statement: Statement): Unit = {}

  @Pointcut("execution(* java.sql.PreparedStatement+.execute()) && this(statement)")
  def statementExecuteWithoutArguments(statement: PreparedStatement): Unit = {}

  @Around("statementExecuteWithArguments(sql, statement)")
  def aroundStatementExecuteWithArguments(pjp: ProceedingJoinPoint, sql: String, statement: Statement): Any =
    track(pjp, statement, sql, StatementTypes.GenericExecute)

  @Around("statementExecuteWithoutArguments(statement)")
  def aroundStatementExecuteWithoutArguments(pjp: ProceedingJoinPoint, statement: PreparedStatement): Any =
    track(pjp, statement, "not-available", StatementTypes.GenericExecute)

  /**
   *   Calls to java.sql.Statement+.executeQuery(..)
   */

  @Pointcut("execution(* java.sql.Statement+.executeQuery(..)) && args(sql) && this(statement)")
  def statementExecuteQueryWithArguments(sql: String, statement: Statement): Unit = {}

  @Pointcut("execution(* java.sql.PreparedStatement+.executeQuery()) && this(statement)")
  def statementExecuteQueryWithoutArguments(statement: PreparedStatement): Unit = {}

  @Around("statementExecuteQueryWithArguments(sql, statement)")
  def aroundStatementExecuteQueryWithArguments(pjp: ProceedingJoinPoint, sql: String, statement: Statement): Any =
    track(pjp, statement, sql, StatementTypes.Query)

  @Around("statementExecuteQueryWithoutArguments(statement)")
  def aroundStatementExecuteQueryWithoutArguments(pjp: ProceedingJoinPoint, statement: PreparedStatement): Any =
    track(pjp, statement, "not-available", StatementTypes.Query)

  /**
   *   Calls to java.sql.Statement+.executeUpdate(..)
   */

  @Pointcut("execution(* java.sql.Statement+.executeUpdate(..)) && args(sql) && this(statement)")
  def statementExecuteUpdateWithArguments(sql: String, statement: Statement): Unit = {}

  @Pointcut("execution(* java.sql.PreparedStatement+.executeUpdate()) && this(statement)")
  def statementExecuteUpdateWithoutArguments(statement: PreparedStatement): Unit = {}

  @Around("statementExecuteUpdateWithArguments(sql, statement)")
  def aroundStatementExecuteUpdateWithArguments(pjp: ProceedingJoinPoint, sql: String, statement: Statement): Any =
    track(pjp, statement, sql, StatementTypes.Update)

  @Around("statementExecuteUpdateWithoutArguments(statement)")
  def aroundStatementExecuteUpdateWithoutArguments(pjp: ProceedingJoinPoint, statement: PreparedStatement): Any =
    track(pjp, statement, "not-available", StatementTypes.Update)

  /**
   *   Calls to java.sql.Statement+.executeBatch() and java.sql.Statement+.executeLargeBatch()
   */

  @Pointcut("(execution(* java.sql.Statement+.executeBatch()) || execution(* java.sql.Statement+.executeLargeBatch()))  && this(statement)")
  def statementExecuteBatch(statement: Statement): Unit = {}

  @Around("statementExecuteBatch(statement)")
  def aroundStatementExecuteBatch(pjp: ProceedingJoinPoint, statement: Statement): Any =
    track(pjp, statement, "not-available", StatementTypes.Batch)

  def track(pjp: ProceedingJoinPoint, target: Any, sql: String, statementType: String): Any = {
    val poolTags = Option(target.asInstanceOf[Mixin.HasConnectionPoolMetrics].connectionPoolMetrics)
      .map(_.tags)
      .getOrElse(Map.empty[String, String])

    val inFlight = Metrics.Statements.InFlight.refine(poolTags)
    inFlight.increment()

    val startTimestamp = Clock.microTimestamp()
    val span = Kamon.currentContext().get(SpanCustomizer.ContextKey).customize {
      val builder = buildSpan(statementType)
        .withStartTimestamp(startTimestamp)
        .withTag("component", "jdbc")
        .withTag("db.statement", sql)

      poolTags.foreach { case (key, value) => builder.withTag(key, value) }
      builder
    }.start()

    try {

      pjp.proceed()

    } catch {
      case t: Throwable =>
        span
          .addSpanTag("error", true)
          .addSpanTag("error.object", t.toString)

        JdbcExtension.onStatementError(sql, t)

    } finally {
      val endTimestamp = Clock.microTimestamp()
      val elapsedTime = endTimestamp - startTimestamp
      span.finish(endTimestamp)
      inFlight.decrement()

      JdbcExtension.onStatementFinish(sql, elapsedTime)
    }
  }
}

object StatementInstrumentation {
  object StatementTypes {
    val Query = "query"
    val Update = "update"
    val Batch = "batch"
    val GenericExecute = "generic-execute"
  }
}

