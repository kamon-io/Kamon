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

import java.sql.{ PreparedStatement, Statement }
import java.util.concurrent.TimeUnit.{ NANOSECONDS ⇒ nanos }

import kamon.jdbc.JdbcExtension
import kamon.metric.instrument.{ Counter, Histogram, MinMaxCounter }
import kamon.trace.{ SegmentCategory, TraceContext, Tracer }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ Around, Aspect, DeclareMixin, Pointcut }

import scala.util.control.NonFatal

@Aspect
class StatementInstrumentation {

  @DeclareMixin("java.sql.Statement+")
  def mixinHasConnectionPoolTrackerToStatement: HasConnectionPoolTracker = HasConnectionPoolTracker()

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
    val tracker = target.asInstanceOf[HasConnectionPoolTracker].connectionPoolTracker
    if (tracker != null) {
      import tracker.recorder
      val latencyRecorder = statementType match {
        case StatementTypes.Query          ⇒ recorder.queries
        case StatementTypes.Update         ⇒ recorder.updates
        case StatementTypes.Batch          ⇒ recorder.batches
        case StatementTypes.GenericExecute ⇒ recorder.genericExecute
      }

      trackExecution(pjp, sql, statementType, latencyRecorder, recorder.inFlightStatements, recorder.errors, recorder.slowStatements)
    } else {
      import JdbcExtension.defaultTracker
      val latencyRecorder = statementType match {
        case StatementTypes.Query          ⇒ defaultTracker.queries
        case StatementTypes.Update         ⇒ defaultTracker.updates
        case StatementTypes.Batch          ⇒ defaultTracker.batches
        case StatementTypes.GenericExecute ⇒ defaultTracker.genericExecute
      }

      trackExecution(pjp, sql, statementType, latencyRecorder, defaultTracker.inFlightStatements, defaultTracker.errors, defaultTracker.slowStatements)
    }
  }

  def trackExecution(pjp: ProceedingJoinPoint, sql: String, statementType: String, latencyHistogram: Histogram,
    inFlightCounter: MinMaxCounter, errorCounter: Counter, slowStatementsCounter: Counter): Any = {

    inFlightCounter.increment()
    val startedAt = System.nanoTime()

    try {

      if (Tracer.currentContext.nonEmpty && JdbcExtension.shouldGenerateSegments)
        withSegment(Tracer.currentContext, statementType, sql)(pjp.proceed())
      else
        pjp.proceed()

    } catch {
      case NonFatal(throwable) ⇒ {
        JdbcExtension.processSqlError(sql, throwable)
        errorCounter.increment()
        throw throwable
      }
    } finally {
      val timeSpent = System.nanoTime() - startedAt
      latencyHistogram.record(timeSpent)
      inFlightCounter.decrement()

      val timeSpentInMillis = nanos.toMillis(timeSpent)
      if (timeSpentInMillis >= JdbcExtension.slowQueryThreshold) {
        slowStatementsCounter.increment()
        JdbcExtension.processSlowQuery(sql, timeSpentInMillis)
      }
    }
  }

  def withSegment[A](ctx: TraceContext, statementType: String, statement: String)(thunk: ⇒ A): A = {
    val segmentName = JdbcExtension.generateJdbcSegmentName(statementType, statement)
    val segment = ctx.startSegment(segmentName, SegmentCategory.Database, JdbcExtension.SegmentLibraryName)
    try thunk finally segment.finish()
  }
}

object StatementTypes {
  val Query = "query"
  val Update = "update"
  val Batch = "batch"
  val GenericExecute = "generic-execute"
}