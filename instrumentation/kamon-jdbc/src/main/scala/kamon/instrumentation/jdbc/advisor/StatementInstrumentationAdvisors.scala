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

package kamon.instrumentation.jdbc.advisor

import java.sql.{PreparedStatement, Statement}
import kamon.instrumentation.jdbc.{HasStatementSQL, StatementMonitor}
import kamon.instrumentation.jdbc.StatementMonitor.{Invocation, StatementTypes}
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice.Thrown

import scala.annotation.static

/**
  * Advisor for java.sql.Statement::execute
  */
class StatementExecuteMethodAdvisor
object StatementExecuteMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  @static def executeStart(@Advice.This statement: Any, @Advice.Argument(0) sql: String): Option[Invocation] = {
    StatementMonitor.start(statement, sql, StatementTypes.GenericExecute)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  @static def executeEnd(@Advice.Enter invocation: Option[Invocation], @Thrown throwable: Throwable): Unit = {
    invocation.foreach(_.close(throwable))
  }
}

/**
  * Advisor for java.sql.PreparedStatement::execute
  */
class PreparedStatementExecuteMethodAdvisor
object PreparedStatementExecuteMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  @static def executeStart(@Advice.This statement: HasStatementSQL): Option[Invocation] = {
    StatementMonitor.start(statement, statement.capturedStatementSQL(), StatementTypes.GenericExecute)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  @static def executeEnd(@Advice.Enter invocation: Option[Invocation], @Thrown throwable: Throwable): Unit = {
    invocation.foreach(_.close(throwable))
  }
}

/**
  * Advisor for java.sql.Statement::executeQuery
  */
class StatementExecuteQueryMethodAdvisor
object StatementExecuteQueryMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  @static def executeStart(@Advice.This statement: Any, @Advice.Argument(0) sql: String): Option[Invocation] = {
    StatementMonitor.start(statement, sql, StatementTypes.Query)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  @static def executeEnd(@Advice.Enter invocation: Option[Invocation], @Thrown throwable: Throwable): Unit = {
    invocation.foreach(_.close(throwable))
  }
}

/**
  *  Advisor for java.sql.PreparedStatement::executeQuery
  */
class PreparedStatementExecuteQueryMethodAdvisor
object PreparedStatementExecuteQueryMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  @static def executeStart(@Advice.This statement: HasStatementSQL): Option[Invocation] = {
    StatementMonitor.start(statement, statement.capturedStatementSQL(), StatementTypes.Query)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  @static def executeEnd(@Advice.Enter invocation: Option[Invocation], @Thrown throwable: Throwable): Unit = {
    invocation.foreach(_.close(throwable))
  }
}

/**
  * Advisor for java.sql.Statement::executeUpdate
  */
class StatementExecuteUpdateMethodAdvisor
object StatementExecuteUpdateMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  @static def executeStart(@Advice.This statement: Any, @Advice.Argument(0) sql: String): Option[Invocation] = {
    StatementMonitor.start(statement, sql, StatementTypes.Update)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  @static def executeEnd(@Advice.Enter invocation: Option[Invocation], @Thrown throwable: Throwable): Unit = {
    invocation.foreach(_.close(throwable))
  }
}

/**
  * Advisor for java.sql.PreparedStatement::executeUpdate
  */
class PreparedStatementExecuteUpdateMethodAdvisor
object PreparedStatementExecuteUpdateMethodAdvisor {
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  @static def executeStart(@Advice.This statement: HasStatementSQL): Option[Invocation] = {
    StatementMonitor.start(statement, statement.capturedStatementSQL(), StatementTypes.Update)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  @static def executeEnd(@Advice.Enter invocation: Option[Invocation], @Thrown throwable: Throwable): Unit = {
    invocation.foreach(_.close(throwable))
  }
}

/**
  * Advisor for java.sql.Statement+::executeBatch
  * Advisor for java.sql.Statement+::executeLargeBatch
  */
class StatementExecuteBatchMethodAdvisor
object StatementExecuteBatchMethodAdvisor {
  // inline
  @Advice.OnMethodEnter(suppress = classOf[Throwable])
  @static def executeStart(@Advice.This statement: Any): Option[Invocation] = {
    val statementSQL = statement match {
      case hSQL: HasStatementSQL => hSQL.capturedStatementSQL()
      case _                     => statement.toString
    }

    StatementMonitor.start(statement, statementSQL, StatementTypes.Batch)
  }

  @Advice.OnMethodExit(onThrowable = classOf[Throwable], suppress = classOf[Throwable])
  @static def executeEnd(@Advice.Enter invocation: Option[Invocation], @Thrown throwable: Throwable): Unit = {
    invocation.foreach(_.close(throwable))
  }
}
