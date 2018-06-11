/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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
import java.time.Instant
import java.time.temporal.ChronoUnit

import kamon.Kamon
import kamon.Kamon.buildSpan
import kamon.jdbc.instrumentation.StatementInstrumentation.StatementTypes
import kamon.jdbc.instrumentation.bridge.MariaPreparedStatement
import kamon.jdbc.instrumentation.maria.{MariaExecuteQueryMethodInterceptor, MariaExecuteUpdateMethodInterceptor}
import kamon.jdbc.instrumentation.mixin.{HasConnectionPoolMetrics, HasConnectionPoolMetricsMixin}
import kamon.jdbc.{Jdbc, Metrics}
import kamon.metric.RangeSampler
import kamon.trace.{Span, SpanCustomizer}
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice.Thrown
import kanela.agent.scala.KanelaInstrumentation


class StatementInstrumentation extends KanelaInstrumentation {

  /**
    * Instrument:
    *
    * java.sql.Statement::execute
    * java.sql.Statement::executeQuery
    * java.sql.Statement::executeUpdate
    * java.sql.Statement::executeBatch
    * java.sql.Statement::executeLargeBatch
    *
    * Mix:
    *
    * java.sql.Statement with kamon.jdbc.instrumentation.mixin.HasConnectionPoolMetrics
    *
    */
  val withOneStringArgument = withArgument(0, classOf[String])

  forSubtypeOf("java.sql.Statement") { builder =>
    builder
       .withMixin(classOf[HasConnectionPoolMetricsMixin])
       .withAdvisorFor(method("execute").and(withOneStringArgument), classOf[StatementExecuteMethodAdvisor])
       .withAdvisorFor(method("executeQuery").and(withOneStringArgument), classOf[StatementExecuteQueryMethodAdvisor])
       .withAdvisorFor(method("executeUpdate").and(withOneStringArgument), classOf[StatementExecuteUpdateMethodAdvisor])
       .withAdvisorFor(anyMethod("executeBatch", "executeLargeBatch"), classOf[StatementExecuteBatchMethodAdvisor])
       .build()
  }


  /**
    * Instrument:
    *
    * java.sql.PreparedStatement::execute
    * java.sql.PreparedStatement::executeQuery
    * java.sql.PreparedStatement::executeUpdate
    * java.sql.PreparedStatement::executeBatch
    * java.sql.PreparedStatement::executeLargeBatch
    *
    */
  forSubtypeOf("java.sql.PreparedStatement") { builder =>
    builder
      .withAdvisorFor(method("execute"), classOf[PreparedStatementExecuteMethodAdvisor])
      .withAdvisorFor(method("executeQuery"), classOf[PreparedStatementExecuteQueryMethodAdvisor])
      .withAdvisorFor(method("executeUpdate"), classOf[PreparedStatementExecuteUpdateMethodAdvisor])
      .build()
  }

  /**
    * Instrument:
    *
    * org.mariadb.jdbc.MariaDbServerPreparedStatement::executeQuery
    * org.mariadb.jdbc.MariaDbServerPreparedStatement::executeUpdate
    */
  forTargetType("org.mariadb.jdbc.MariaDbServerPreparedStatement") { builder =>
    builder
      .withBridge(classOf[MariaPreparedStatement])
      .withInterceptorFor(method("executeQuery"), MariaExecuteQueryMethodInterceptor)
      .withInterceptorFor(method("executeUpdate"), MariaExecuteUpdateMethodInterceptor)
      .build()
  }
}

object StatementInstrumentation {
  object StatementTypes {
    val Query = "query"
    val Update = "update"
    val Batch = "batch"
    val GenericExecute = "generic-execute"
  }

  def trackStart(target: Any,
                 sql: String,
                 statementType: String): (Span, String, Instant, RangeSampler) = {

    val poolTags = Option(target.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics)
      .map(_.tags)
      .getOrElse(Map.empty[String, String])

    val inFlight = Metrics.Statements.InFlight.refine(poolTags)
    inFlight.increment()

    val startTimestamp = Kamon.clock().instant()
    val span = Kamon.currentContext().get(SpanCustomizer.ContextKey).customize {
      val builder = buildSpan(statementType)
        .withFrom(startTimestamp)
        .withTag("component", "jdbc")
        .withTag("db.statement", sql)

      poolTags.foreach { case (key, value) => builder.withTag(key, value) }
      builder
    }.start()

    (span, sql, startTimestamp, inFlight)
  }

  def trackEnd(traveler:(Span, String, Instant, RangeSampler),
               throwable: Throwable): Unit = {

    val (span, sql, startTimestamp, inFlight) = traveler

    if(throwable != null) {
      span.addError("error.object", throwable)
      Jdbc.onStatementError(sql, throwable)
    }

    val endTimestamp = Kamon.clock().instant()
    val elapsedTime = startTimestamp.until(endTimestamp, ChronoUnit.MICROS)
    span.finish(endTimestamp)
    inFlight.decrement()

    Jdbc.onStatementFinish(sql, elapsedTime)
  }
}

/**
  * Advisor for java.sql.Statement::execute
  */
class StatementExecuteMethodAdvisor
object StatementExecuteMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: Statement, @Advice.Argument(0) sql: String): (Span, String, Instant, RangeSampler) = {
    StatementInstrumentation.trackStart(statement, sql, StatementTypes.GenericExecute)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementInstrumentation.trackEnd(traveler, throwable)
  }
}

/**
  * Advisor for java.sql.PreparedStatement::execute
  */
class PreparedStatementExecuteMethodAdvisor
object PreparedStatementExecuteMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: PreparedStatement): (Span, String, Instant, RangeSampler) = {
    StatementInstrumentation.trackStart(statement, statement.toString, StatementTypes.GenericExecute)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementInstrumentation.trackEnd(traveler, throwable)
  }
}


/**
  * Advisor for java.sql.Statement::executeQuery
  */
class StatementExecuteQueryMethodAdvisor
object StatementExecuteQueryMethodAdvisor  {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: Statement, @Advice.Argument(0) sql: String): (Span, String, Instant, RangeSampler) = {
    StatementInstrumentation.trackStart(statement, sql, StatementTypes.Query)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementInstrumentation.trackEnd(traveler, throwable)
  }
}

/**
*  Advisor for java.sql.PreparedStatement::executeQuery
*/
class PreparedStatementExecuteQueryMethodAdvisor
object PreparedStatementExecuteQueryMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: PreparedStatement): (Span, String, Instant, RangeSampler) = {
    StatementInstrumentation.trackStart(statement, statement.toString, StatementTypes.Query)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementInstrumentation.trackEnd(traveler, throwable)
  }
}

/**
  * Advisor for java.sql.Statement::executeUpdate
  */
class StatementExecuteUpdateMethodAdvisor
object StatementExecuteUpdateMethodAdvisor  {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: Statement, @Advice.Argument(0) sql: String): (Span, String, Instant, RangeSampler) = {
    StatementInstrumentation.trackStart(statement, sql, StatementTypes.Update)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementInstrumentation.trackEnd(traveler, throwable)
  }
}

/**
  * Advisor for java.sql.PreparedStatement::executeUpdate
  */
class PreparedStatementExecuteUpdateMethodAdvisor
object PreparedStatementExecuteUpdateMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: PreparedStatement): (Span, String, Instant, RangeSampler) = {
    StatementInstrumentation.trackStart(statement, statement.toString, StatementTypes.Update)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementInstrumentation.trackEnd(traveler, throwable)
  }
}

/**
  * Advisor for java.sql.Statement+::executeBatch
  * Advisor for java.sql.Statement+::executeLargeBatch
  */
class StatementExecuteBatchMethodAdvisor
object StatementExecuteBatchMethodAdvisor  {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: Statement): (Span, String, Instant, RangeSampler) = {
    StatementInstrumentation.trackStart(statement, statement.toString, StatementTypes.Batch)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementInstrumentation.trackEnd(traveler, throwable)
  }
}
