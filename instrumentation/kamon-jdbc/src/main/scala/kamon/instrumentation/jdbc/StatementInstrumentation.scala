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

package kamon.instrumentation.jdbc

import java.sql.PreparedStatement
import java.util.Properties
import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.instrumentation.jdbc.advisor._
import kamon.tag.TagSet
import kamon.trace.Hooks
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.api.instrumentation.bridge.FieldBridge
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.util.Try
import kanela.agent.libs.net.bytebuddy.matcher.ElementMatchers._

import scala.annotation.static

class StatementInstrumentation extends InstrumentationBuilder {

  private val withOneStringArgument = withArgument(0, classOf[String])

  /**
    * This entire sequence of instrumentation tries to follow the entire JDBC infrastructure: capturing the Database
    * basic information (e.g. vendor and URL) when the Driver creates a Connection, then pass that information down to
    * the Statement/PreparedStatement implementations so that they can be finally included in the traces and metrics.
    */
  onSubTypesOf("java.sql.Driver")
    .advise(method("connect").and(withArgument(0, classOf[String])), DriverConnectAdvice)

  onSubTypesOf("java.sql.Connection")
    .mixin(classOf[HasDatabaseTags.Mixin])
    .mixin(classOf[HasConnectionPoolTelemetry.Mixin])
    .advise(method("isValid"), ConnectionIsValidAdvice)
    .advise(method("prepareCall"), CreatePreparedStatementAdvice)
    .advise(method("prepareStatement"), CreatePreparedStatementAdvice)
    .advise(method("createStatement"), CreateStatementAdvice)

  onSubTypesOf("java.sql.Statement")
    .mixin(classOf[HasStatementSQL.Mixin])
    .mixin(classOf[HasDatabaseTags.Mixin])
    .mixin(classOf[HasConnectionPoolTelemetry.Mixin])

  onTypesMatching(hasSuperType(named("java.sql.Statement")).and(not(nameStartsWith("com.zaxxer.hikari"))))
    .advise(method("execute").and(withOneStringArgument), classOf[StatementExecuteMethodAdvisor])
    .advise(method("executeQuery").and(withOneStringArgument), classOf[StatementExecuteQueryMethodAdvisor])
    .advise(method("executeUpdate").and(withOneStringArgument), classOf[StatementExecuteUpdateMethodAdvisor])
    .advise(anyMethods("executeBatch", "executeLargeBatch"), classOf[StatementExecuteBatchMethodAdvisor])

  onTypesMatching(hasSuperType(named("java.sql.PreparedStatement")).and(not(nameStartsWith("com.zaxxer.hikari"))))
    .advise(method("execute"), classOf[PreparedStatementExecuteMethodAdvisor])
    .advise(method("executeQuery"), classOf[PreparedStatementExecuteQueryMethodAdvisor])
    .advise(method("executeUpdate"), classOf[PreparedStatementExecuteUpdateMethodAdvisor])

  /**
    * We are specifically targeting some of the SQLite Driver classes because due to the way in which inheritance is
    * handled within the Driver these advices were not applied at all. All other drivers tested so far should work just
    * fine with the generic instrumentation.
    */
  onType("org.sqlite.jdbc3.JDBC3PreparedStatement")
    .advise(method("execute"), classOf[PreparedStatementExecuteMethodAdvisor])
    .advise(method("executeQuery"), classOf[PreparedStatementExecuteQueryMethodAdvisor])
    .advise(method("executeUpdate"), classOf[PreparedStatementExecuteUpdateMethodAdvisor])

  onType("org.sqlite.jdbc3.JDBC3Statement")
    .advise(method("execute").and(withOneStringArgument), classOf[StatementExecuteMethodAdvisor])
    .advise(method("executeQuery").and(withOneStringArgument), classOf[StatementExecuteQueryMethodAdvisor])
    .advise(method("executeUpdate").and(withOneStringArgument), classOf[StatementExecuteUpdateMethodAdvisor])
    .advise(anyMethods("executeBatch", "executeLargeBatch"), classOf[StatementExecuteBatchMethodAdvisor])

  /**
    * Since Postgres is reusing the same statement in the implementation if isAlive, we need to "refresh" the
    * information in that Statement to have the proper pool information and ensure that the check round trips will be
    * observed appropriately.
    */
  onTypesMatching(named("org.postgresql.jdbc.PgConnection").and(declaresField(named("checkConnectionQuery"))))
    .bridge(classOf[PgConnectionIsAliveAdvice.PgConnectionPrivateAccess])
    .advise(method("isValid"), PgConnectionIsAliveAdvice)

}

case class DatabaseTags(metricTags: TagSet, spanTags: TagSet)

trait HasDatabaseTags {
  def databaseTags(): DatabaseTags
  def setDatabaseTags(databaseTags: DatabaseTags): Unit
}

object HasDatabaseTags {

  class Mixin(@volatile var databaseTags: DatabaseTags) extends HasDatabaseTags {
    override def setDatabaseTags(databaseTags: DatabaseTags): Unit =
      this.databaseTags = databaseTags
  }
}

trait HasStatementSQL {
  def capturedStatementSQL(): String
  def setStatementSQL(sql: String): Unit
}

object HasStatementSQL {

  class Mixin(@volatile var capturedStatementSQL: String) extends HasStatementSQL {
    override def setStatementSQL(sql: String): Unit =
      this.capturedStatementSQL = sql
  }
}

class DriverConnectAdvice
object DriverConnectAdvice {

  @Advice.OnMethodExit
  @static def exit(
    @Advice.Argument(0) url: String,
    @Advice.Argument(1) properties: Properties,
    @Advice.Return connection: Any
  ): Unit = {

    // The connection could be null if there is more than one registered driver and the DriverManager is looping
    // through them to figure out which one accepts the URL.
    if (connection != null) {
      connection.asInstanceOf[HasDatabaseTags].setDatabaseTags(createDatabaseTags(url, properties))
    }
  }

  private def createDatabaseTags(url: String, properties: Properties): DatabaseTags = {
    val splitUrl = url.split(':')
    val vendorPrefix = Try(splitUrl(1)).getOrElse("unknown")

    // TODO: Include some logic to figure out the actual database name based on the URL and/or properties.
    DatabaseTags(
      metricTags = TagSet.of("db.vendor", vendorPrefix),
      spanTags = TagSet.of("db.url", url)
    )
  }
}

class CreateStatementAdvice
object CreateStatementAdvice {

  @Advice.OnMethodExit
  @static def exit(@Advice.This connection: Any, @Advice.Return statement: Any): Unit = {
    statement.asInstanceOf[HasDatabaseTags].setDatabaseTags(connection.asInstanceOf[HasDatabaseTags].databaseTags())
    statement.asInstanceOf[HasConnectionPoolTelemetry].setConnectionPoolTelemetry(
      connection.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry
    )
  }
}

class CreatePreparedStatementAdvice
object CreatePreparedStatementAdvice {

  @Advice.OnMethodExit
  @static def exit(
    @Advice.This connection: Any,
    @Advice.Argument(0) sql: String,
    @Advice.Return statement: Any
  ): Unit = {
    statement.asInstanceOf[HasDatabaseTags].setDatabaseTags(connection.asInstanceOf[HasDatabaseTags].databaseTags())
    statement.asInstanceOf[HasConnectionPoolTelemetry].setConnectionPoolTelemetry(
      statement.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry
    )
    statement.asInstanceOf[HasStatementSQL].setStatementSQL(sql)
  }
}

class ConnectionIsValidAdvice
object ConnectionIsValidAdvice {
  import Hooks.PreStart

  @Advice.OnMethodEnter
  @static def enter(): Scope =
    Kamon.storeContext(Kamon.currentContext().withEntry(PreStart.Key, PreStart.updateOperationName("isValid")))

  @Advice.OnMethodExit
  @static def exit(@Advice.Enter scope: Scope): Unit =
    scope.close()
}

class PgConnectionIsAliveAdvice
object PgConnectionIsAliveAdvice {

  trait PgConnectionPrivateAccess {

    @FieldBridge("checkConnectionQuery")
    def getCheckConnectionStatement(): PreparedStatement
  }

  @Advice.OnMethodEnter
  @static def enter(@Advice.This connection: Any): Unit = {
    if (connection != null) {
      val statement = connection.asInstanceOf[PgConnectionPrivateAccess].getCheckConnectionStatement()

      if (statement != null) {
        val connectionPoolTelemetry = connection.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry
        statement.asInstanceOf[HasConnectionPoolTelemetry].setConnectionPoolTelemetry(connectionPoolTelemetry)
      }
    }
  }
}
