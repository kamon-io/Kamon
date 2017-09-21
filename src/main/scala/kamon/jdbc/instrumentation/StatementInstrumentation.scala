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
import java.util.concurrent.Callable

import kamon.Kamon
import kamon.Kamon.buildSpan
import kamon.agent.libs.net.bytebuddy.implementation.bind.annotation
import kamon.agent.libs.net.bytebuddy.implementation.bind.annotation.{RuntimeType, SuperCall, This}
import kamon.agent.scala.KamonInstrumentation
import kamon.jdbc.instrumentation.StatementInstrumentation.StatementTypes
import kamon.jdbc.instrumentation.mixin.{HasConnectionPoolMetrics, HasConnectionPoolMetricsMixin}
import kamon.jdbc.{Jdbc, Metrics}
import kamon.trace.SpanCustomizer
import kamon.util.Clock


class StatementInstrumentation extends KamonInstrumentation {


  override def order() = 1000

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
       .withInterceptorFor(method("execute").and(withOneStringArgument), ExecuteMethodInterceptor)
       .withInterceptorFor(method("executeQuery").and(withOneStringArgument), ExecuteQueryMethodInterceptor)
       .withInterceptorFor(method("executeUpdate").and(withOneStringArgument), ExecuteUpdateMethodInterceptor)
       .withInterceptorFor(anyMethod("executeBatch", "executeLargeBatch"), ExecuteBatchMethodInterceptor)
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
    * Mix:
    *
    * java.sql.Statement with kamon.jdbc.instrumentation.mixin.HasConnectionPoolMetrics
    *
    */
  forSubtypeOf("java.sql.PreparedStatement") { builder =>
    builder
      .withMixin(classOf[HasConnectionPoolMetricsMixin])
      .withInterceptorFor(method("execute"), ExecuteMethodInterceptor)
      .withInterceptorFor(method("executeQuery"), ExecuteQueryMethodInterceptor)
      .withInterceptorFor(method("executeUpdate"), ExecuteUpdateMethodInterceptor)
      .withInterceptorFor(anyMethod("executeBatch", "executeLargeBatch"), ExecuteBatchMethodInterceptor)
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

  def track(callable: Callable[_], target: Any, sql: String, statementType: String): Any = {
    val poolTags = Option(target.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics)
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

      callable.call()

    } catch {
      case t: Throwable =>
        span.addError("error.object", t)
        Jdbc.onStatementError(sql, t)

    } finally {
      val endTimestamp = Clock.microTimestamp()
      val elapsedTime = endTimestamp - startTimestamp
      span.finish(endTimestamp)
      inFlight.decrement()

      Jdbc.onStatementFinish(sql, elapsedTime)
    }
  }
}

/**
  * Interceptor for java.sql.Statement::execute
  * Interceptor for java.sql.PreparedStatement::execute
  */
object ExecuteMethodInterceptor {

  @RuntimeType
  def execute(@SuperCall callable: Callable[_], @This statement: Statement, @annotation.Argument(0) sql: String): Any = {
    StatementInstrumentation.track(callable, statement, sql, StatementTypes.GenericExecute)
  }

  @RuntimeType
  def execute(@SuperCall callable: Callable[_], @This statement: PreparedStatement): Any = {
    StatementInstrumentation.track(callable, statement, statement.toString, StatementTypes.GenericExecute)
  }
}


/**
  * Interceptor for java.sql.Statement::executeQuery
  * Interceptor for java.sql.PreparedStatement::executeQuery
  */
object ExecuteQueryMethodInterceptor {

  @RuntimeType
  def executeQuery(@SuperCall callable: Callable[_], @This statement: Statement, @annotation.Argument(0) sql: String): Any = {
    StatementInstrumentation.track(callable, statement, sql, StatementTypes.Query)
  }

  @RuntimeType
  def executeQuery(@SuperCall callable: Callable[_], @This statement: PreparedStatement): Any = {
    StatementInstrumentation.track(callable, statement, statement.toString, StatementTypes.Query)
  }
}

/**
  * Interceptor for java.sql.Statement::executeUpdate
  * Interceptor for java.sql.PreparedStatement::executeUpdate
  */
object ExecuteUpdateMethodInterceptor {

  @RuntimeType
  def executeUpdate(@SuperCall callable: Callable[_], @This statement:Statement, @annotation.Argument(0) sql:String): Any = {
    StatementInstrumentation.track(callable, statement,  sql, StatementTypes.Update)
  }

  @RuntimeType
  def executeUpdate(@SuperCall callable: Callable[_], @This statement:PreparedStatement): Any = {
    StatementInstrumentation.track(callable, statement,  statement.toString , StatementTypes.Update)
  }
}

/**
  * Interceptor for java.sql.Statement::executeBatch
  * Interceptor for java.sql.Statement::executeLargeBatch
  * Interceptor for java.sql.PreparedStatement::executeBatch
  * Interceptor for java.sql.PreparedStatement::executeLargeBatch
  */
object ExecuteBatchMethodInterceptor {

  @RuntimeType
  def executeUpdate(@SuperCall callable: Callable[_], @This statement:Statement): Any =
    StatementInstrumentation.track(callable, statement,  statement.toString , StatementTypes.Batch)
}
