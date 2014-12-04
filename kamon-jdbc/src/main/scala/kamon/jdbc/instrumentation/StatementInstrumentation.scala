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

import akka.actor.ActorSystem
import kamon.Kamon
import kamon.jdbc.Jdbc
import kamon.jdbc.metric.StatementsMetrics
import kamon.jdbc.metric.StatementsMetricsGroupFactory.GroupRecorder
import kamon.metric.Metrics
import kamon.trace.{ TraceContext, SegmentCategory, TraceRecorder }
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ Around, Aspect, Pointcut }
import org.slf4j.LoggerFactory

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
    TraceRecorder.withTraceContextAndSystem { (ctx, system) ⇒

      implicit val statementRecorder: Option[GroupRecorder] = Kamon(Metrics)(system).register(StatementsMetrics(Statements), StatementsMetrics.Factory)

      sql.replaceAll(CommentPattern, Empty) match {
        case SelectStatement(_) ⇒ withSegment(ctx, system, Select)(recordRead(pjp, sql, system))
        case InsertStatement(_) ⇒ withSegment(ctx, system, Insert)(recordWrite(pjp, sql, system))
        case UpdateStatement(_) ⇒ withSegment(ctx, system, Update)(recordWrite(pjp, sql, system))
        case DeleteStatement(_) ⇒ withSegment(ctx, system, Delete)(recordWrite(pjp, sql, system))
        case anythingElse ⇒
          log.debug(s"Unable to parse sql [$sql]")
          pjp.proceed()
      }
    }
  } getOrElse pjp.proceed()

  def withTimeSpent[A](thunk: ⇒ A)(timeSpent: Long ⇒ Unit): A = {
    val start = System.nanoTime()
    try thunk finally timeSpent(System.nanoTime() - start)
  }

  def withSegment[A](ctx: TraceContext, system: ActorSystem, statement: String)(thunk: ⇒ A): A = {
    val segmentName = Jdbc(system).generateJdbcSegmentName(statement)
    val segment = ctx.startSegment(segmentName, SegmentCategory.Database, Jdbc.SegmentLibraryName)
    try thunk finally segment.finish()
  }

  def recordRead(pjp: ProceedingJoinPoint, sql: String, system: ActorSystem)(implicit statementRecorder: Option[GroupRecorder]): Any = {
    withTimeSpent(pjp.proceedWithErrorHandler(sql, system)) { timeSpent ⇒
      statementRecorder.map(stmr ⇒ stmr.reads.record(timeSpent))

      val timeSpentInMillis = nanos.toMillis(timeSpent)

      if (timeSpentInMillis >= Jdbc(system).slowQueryThreshold) {
        statementRecorder.map(stmr ⇒ stmr.slow.increment())
        Jdbc(system).processSlowQuery(sql, timeSpentInMillis)
      }
    }
  }

  def recordWrite(pjp: ProceedingJoinPoint, sql: String, system: ActorSystem)(implicit statementRecorder: Option[GroupRecorder]): Any = {
    withTimeSpent(pjp.proceedWithErrorHandler(sql, system)) { timeSpent ⇒
      statementRecorder.map(stmr ⇒ stmr.writes.record(timeSpent))
    }
  }
}

object StatementInstrumentation {
  val log = LoggerFactory.getLogger(classOf[StatementInstrumentation])

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
    def proceedWithErrorHandler(sql: String, system: ActorSystem)(implicit statementRecorder: Option[GroupRecorder]): Any = {
      try {
        pjp.proceed()
      } catch {
        case NonFatal(cause) ⇒
          Jdbc(system).processSqlError(sql, cause)
          statementRecorder.map(stmr ⇒ stmr.errors.increment())
          throw cause
      }
    }
  }
}

