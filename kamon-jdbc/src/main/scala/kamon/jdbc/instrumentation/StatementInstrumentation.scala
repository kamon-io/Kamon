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

import java.sql.SQLException

import akka.actor.ActorSystem
import kamon.Kamon
import kamon.jdbc.Jdbc
import kamon.jdbc.metric.StatementsMetrics
import kamon.jdbc.metric.StatementsMetrics.StatementsMetricsRecorder
import kamon.metric.Metrics
import kamon.trace.TraceRecorder
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{ AfterThrowing, Around, Aspect, Pointcut }
import org.slf4j.LoggerFactory

@Aspect
class StatementInstrumentation {

  import StatementInstrumentation._

  @volatile var statementRecorder: Option[StatementsMetricsRecorder] = Option.empty

  @Pointcut("execution(* java.sql.Statement.execute*(..)) && args(sql)")
  def onExecuteStatement(sql: String): Unit = {}

  @Pointcut("execution(* java.sql.Connection.prepareStatement(..)) && args(sql)")
  def onExecutePreparedStatement(sql: String): Unit = {}

  @Pointcut("execution(* java.sql.Connection.prepareCall(..)) && args(sql)")
  def onExecutePreparedCall(sql: String): Unit = {}

  @Around("onExecuteStatement(sql) || onExecutePreparedStatement(sql) || onExecutePreparedCall(sql)")
  def aroundExecuteStatement(pjp: ProceedingJoinPoint, sql: String): Any = {
    TraceRecorder.withTraceContextAndSystem { (ctx, system) ⇒

      if (statementRecorder.nonEmpty) {
        statementRecorder = Kamon(Metrics)(system).register(StatementsMetrics("Statements"), StatementsMetrics.Factory)
      }

      sql.replaceAll(CommentPattern, "") match {
        case SelectStatement(_) ⇒ recordRead(pjp, sql)(system)
        case InsertStatement(_) | UpdateStatement(_) | DeleteStatement(_) ⇒ recordWrite(pjp)
        case anythingElse ⇒
          log.debug(s"Unable to parse sql [$sql]")
          pjp.proceed()
      }
    } getOrElse pjp.proceed()
  }

  @AfterThrowing(pointcut = "onExecuteStatement(sql) || onExecutePreparedStatement(sql) || onExecutePreparedCall(sql)", throwing = "ex")
  def onError(sql: String, ex:SQLException): Unit = {
    log.error(s"the query [$sql] failed with exception [${ex.getMessage}]")
    statementRecorder.map(stmr ⇒ stmr.errors.increment())
  }

  def withTimeSpent[A](thunk: ⇒ A)(timeSpent: Long ⇒ Unit): A = {
    val start = System.nanoTime()
    try thunk finally timeSpent(System.nanoTime() - start)
  }

  def recordRead(pjp: ProceedingJoinPoint, sql: String)(system: ActorSystem): Any = {
    withTimeSpent(pjp.proceed()) {
      timeSpent ⇒
        statementRecorder.map(stmr ⇒ stmr.reads.record(timeSpent))

        if (timeSpent >= Jdbc(system).slowQueryThreshold) {
          statementRecorder.map(stmr ⇒ stmr.slow.increment())
          Jdbc(system).processSlowQuery(sql, timeSpent)
        }
    }
  }

  def recordWrite(pjp: ProceedingJoinPoint): Any = {
    withTimeSpent(pjp.proceed()) {
      timeSpent ⇒ statementRecorder.map(stmr ⇒ stmr.writes.record(timeSpent))
    }
  }
}

object StatementInstrumentation {
  val log = LoggerFactory.getLogger("StatementInstrumentation")

  val SelectStatement = "(?i)^\\s*select.*?\\sfrom[\\s\\[]+([^\\]\\s,)(;]*).*".r
  val InsertStatement = "(?i)^\\s*insert(?:\\s+ignore)?\\s+into\\s+([^\\s(,;]*).*".r
  val UpdateStatement = "(?i)^\\s*update\\s+([^\\s,;]*).*".r
  val DeleteStatement = "(?i)^\\s*delete\\s+from\\s+([^\\s,(;]*).*".r
  val CommentPattern = "/\\*.*?\\*/" //for now only removes comments of kind / * anything * /
}

