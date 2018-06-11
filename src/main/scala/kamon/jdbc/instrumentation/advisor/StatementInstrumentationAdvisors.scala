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

package kamon.jdbc.instrumentation.advisor

import java.sql.{PreparedStatement, Statement}
import java.time.Instant

import kamon.jdbc.instrumentation.StatementMonitor
import kamon.jdbc.instrumentation.StatementMonitor.StatementTypes
import kamon.metric.RangeSampler
import kamon.trace.Span
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice.Thrown

/**
  * Advisor for java.sql.Statement::execute
  */
class StatementExecuteMethodAdvisor
object StatementExecuteMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: Statement, @Advice.Argument(0) sql: String): (Span, String, Instant, RangeSampler) = {
    StatementMonitor.trackStart(statement, sql, StatementTypes.GenericExecute)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementMonitor.trackEnd(traveler, throwable)
  }
}

/**
  * Advisor for java.sql.PreparedStatement::execute
  */
class PreparedStatementExecuteMethodAdvisor
object PreparedStatementExecuteMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: PreparedStatement): (Span, String, Instant, RangeSampler) = {
    StatementMonitor.trackStart(statement, statement.toString, StatementTypes.GenericExecute)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementMonitor.trackEnd(traveler, throwable)
  }
}


/**
  * Advisor for java.sql.Statement::executeQuery
  */
class StatementExecuteQueryMethodAdvisor
object StatementExecuteQueryMethodAdvisor  {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: Statement, @Advice.Argument(0) sql: String): (Span, String, Instant, RangeSampler) = {
    StatementMonitor.trackStart(statement, sql, StatementTypes.Query)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementMonitor.trackEnd(traveler, throwable)
  }
}

/**
  *  Advisor for java.sql.PreparedStatement::executeQuery
  */
class PreparedStatementExecuteQueryMethodAdvisor
object PreparedStatementExecuteQueryMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: PreparedStatement): (Span, String, Instant, RangeSampler) = {
    StatementMonitor.trackStart(statement, statement.toString, StatementTypes.Query)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementMonitor.trackEnd(traveler, throwable)
  }
}

/**
  * Advisor for java.sql.Statement::executeUpdate
  */
class StatementExecuteUpdateMethodAdvisor
object StatementExecuteUpdateMethodAdvisor  {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: Statement, @Advice.Argument(0) sql: String): (Span, String, Instant, RangeSampler) = {
    StatementMonitor.trackStart(statement, sql, StatementTypes.Update)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementMonitor.trackEnd(traveler, throwable)
  }
}

/**
  * Advisor for java.sql.PreparedStatement::executeUpdate
  */
class PreparedStatementExecuteUpdateMethodAdvisor
object PreparedStatementExecuteUpdateMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  def executeStart(@Advice.This statement: PreparedStatement): (Span, String, Instant, RangeSampler) = {
    StatementMonitor.trackStart(statement, statement.toString, StatementTypes.Update)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementMonitor.trackEnd(traveler, throwable)
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
    StatementMonitor.trackStart(statement, statement.toString, StatementTypes.Batch)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  def executeEnd(@Advice.Enter traveler:(Span, String, Instant, RangeSampler), @Thrown throwable: Throwable): Unit = {
    StatementMonitor.trackEnd(traveler, throwable)
  }
}
