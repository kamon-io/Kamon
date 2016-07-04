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

import java.util.concurrent.TimeUnit.{ NANOSECONDS ⇒ nanos }

import kamon.Kamon
import kamon.jdbc.JdbcExtension
import kamon.jdbc.metric.StatementsMetrics
import kamon.trace.{ Tracer, TraceContext, SegmentCategory }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ Around, Aspect, Pointcut }

import scala.util.control.NonFatal

@Aspect
class StatementInstrumentation {

  import StatementInstrumentation._

  @Pointcut("call(* java.sql.Statement.execute*(..)) && args(sql)")
  def onExecuteStatement(sql: String): Unit = {}

  @Pointcut("call(* java.sql.Connection.prepareStatement(..)) && args(sql)")
  def onExecutePreparedStatement(sql: String): Unit = {}

  @Pointcut("call(* java.sql.Connection.prepareCall(..)) && args(sql)")
  def onExecutePreparedCall(sql: String): Unit = {}

  @Around("onExecuteStatement(sql) || onExecutePreparedStatement(sql) || onExecutePreparedCall(sql)")
  def aroundExecuteStatement(pjp: ProceedingJoinPoint, sql: String): Any = {
    Tracer.currentContext.collect { ctx ⇒
      implicit val statementRecorder = Kamon.metrics.entity(StatementsMetrics, "jdbc-statements")

      sql.replaceAll(CommentPattern, Empty) match {
        case SelectStatement(_) ⇒ withSegment(ctx, Select)(recordRead(pjp, sql))
        case InsertStatement(_) ⇒ withSegment(ctx, Insert)(recordWrite(pjp, sql))
        case UpdateStatement(_) ⇒ withSegment(ctx, Update)(recordWrite(pjp, sql))
        case DeleteStatement(_) ⇒ withSegment(ctx, Delete)(recordWrite(pjp, sql))
        case anythingElse ⇒
          JdbcExtension.log.debug(s"Unable to parse sql [$sql]")
          pjp.proceed()
      }
    }
  } getOrElse pjp.proceed()

  def withTimeSpent[A](thunk: ⇒ A)(timeSpent: Long ⇒ Unit): A = {
    val start = System.nanoTime()
    try thunk finally timeSpent(System.nanoTime() - start)
  }

  def withSegment[A](ctx: TraceContext, statement: String)(thunk: ⇒ A): A = {
    val segmentName = JdbcExtension.generateJdbcSegmentName(statement)
    val segment = ctx.startSegment(segmentName, SegmentCategory.Database, JdbcExtension.SegmentLibraryName)
    try thunk finally segment.finish()
  }

  def recordRead(pjp: ProceedingJoinPoint, sql: String)(implicit statementRecorder: StatementsMetrics): Any = {
    withTimeSpent(pjp.proceedWithErrorHandler(sql)) { timeSpent ⇒
      statementRecorder.reads.record(timeSpent)

      val timeSpentInMillis = nanos.toMillis(timeSpent)

      if (timeSpentInMillis >= JdbcExtension.slowQueryThreshold) {
        statementRecorder.slows.increment()
        JdbcExtension.processSlowQuery(sql, timeSpentInMillis)
      }
    }
  }

  def recordWrite(pjp: ProceedingJoinPoint, sql: String)(implicit statementRecorder: StatementsMetrics): Any = {
    withTimeSpent(pjp.proceedWithErrorHandler(sql)) { timeSpent ⇒
      statementRecorder.writes.record(timeSpent)
    }
  }
}

object StatementInstrumentation {

  val SelectStatement = "(?i)^\\s*select.*?\\sfrom[\\s\\[]+([^\\]\\s,)(;]*).*".r
  val InsertStatement = "(?i)^\\s*insert(?:\\s+ignore)?\\s+into\\s+([^\\s(,;]*).*".r
  val UpdateStatement = "(?i)^\\s*update\\s+([^\\s,;]*).*".r
  val DeleteStatement = "(?i)^\\s*delete\\s+from\\s+([^\\s,(;]*).*".r
  val CommentPattern = "/\\*.*?\\*/" //for now only removes comments of kind / * anything * /
  val Empty = ""
  val Statements = "jdbc-statements"
  val Select = "Select"
  val Insert = "Insert"
  val Update = "Update"
  val Delete = "Delete"

  implicit class PimpedProceedingJoinPoint(pjp: ProceedingJoinPoint) {
    def proceedWithErrorHandler(sql: String)(implicit statementRecorder: StatementsMetrics): Any = {
      try {
        pjp.proceed()
      } catch {
        case NonFatal(cause) ⇒
          JdbcExtension.processSqlError(sql, cause)
          statementRecorder.errors.increment()
          throw cause
      }
    }
  }
}

