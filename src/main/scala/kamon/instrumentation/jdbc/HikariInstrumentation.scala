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

package kamon.instrumentation.jdbc

import java.sql.{Connection, Statement}

import com.zaxxer.hikari.HikariConfig
import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.instrumentation.jdbc.JdbcMetrics.ConnectionPoolInstruments
import kamon.tag.TagSet
import kamon.trace.Hooks
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice._

import scala.util.Try

class HikariInstrumentation extends InstrumentationBuilder {

  /**
    *
    */
  onType("com.zaxxer.hikari.pool.HikariPool")
    .mixin(classOf[HasConnectionPoolTelemetry.Mixin])
    .advise(isConstructor(), HikariPoolConstructorAdvice)
    .advise(method("shutdown"), HikariPoolShutdownMethodAdvice)
    .advise(method("createPoolEntry"), HikariPoolCreatePoolEntryMethodAdvice)
    .advise(method("closeConnection"), HikariPoolCloseConnectionMethodAdvice)
    .advise(method("createTimeoutException"), HikariPoolCreateTimeoutExceptionMethodAdvice)
    .advise(method("getConnection").and(takesArguments(0)), HikariPoolGetConnectionAdvice)

  onType("com.zaxxer.hikari.pool.PoolBase")
    .advise(method("setupConnection"), PoolBaseNewConnectionAdvice)

  /**
    *
    */
  onType("com.zaxxer.hikari.pool.ProxyConnection")
    .advise(method("close"), ProxyConnectionCloseMethodAdvice)
    .advise(method("prepareStatement"), ProxyConnectionStatementMethodsAdvice)
    .advise(method("createStatement"), ProxyConnectionStatementMethodsAdvice)
}

case class ConnectionPoolTelemetry(instruments: ConnectionPoolInstruments, databaseTags: DatabaseTags)

trait HasConnectionPoolTelemetry {
  def connectionPoolTelemetry: ConnectionPoolTelemetry
  def setConnectionPoolTelemetry(cpTelemetry: ConnectionPoolTelemetry): Unit
}

object HasConnectionPoolTelemetry {

  class Mixin(@volatile var connectionPoolTelemetry: ConnectionPoolTelemetry) extends HasConnectionPoolTelemetry {
    override def setConnectionPoolTelemetry(cpTelemetry: ConnectionPoolTelemetry): Unit =
      this.connectionPoolTelemetry = cpTelemetry
  }
}

object HikariPoolConstructorAdvice {

  @Advice.OnMethodExit
  def exit(@Advice.This hikariPool: HasConnectionPoolTelemetry, @Advice.Argument(0) config: HikariConfig): Unit = {
    val url = config.getJdbcUrl()
    val vendor = Try(url.split(':')(1)).getOrElse("unknown")

    val metricTags = TagSet.builder()
      .add("jdbc.pool.vendor", "hikari")
      .add("jdbc.pool.name", config.getPoolName)
      .add("db.vendor", vendor)
      .build()

    val spanTags = TagSet.builder()
      .add("db.url", url)
      .build()

    val poolInstruments = JdbcMetrics.poolInstruments(metricTags)
    hikariPool.setConnectionPoolTelemetry(ConnectionPoolTelemetry(poolInstruments, DatabaseTags(metricTags, spanTags)))
  }
}

object HikariPoolShutdownMethodAdvice {

  @Advice.OnMethodExit
  def exit(@This hikariPool: Object): Unit =
    hikariPool.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry.instruments.remove()
}

object HikariPoolCreatePoolEntryMethodAdvice {

  @Advice.OnMethodExit
  def exit(@This hikariPool: HasConnectionPoolTelemetry): Unit = {
    val poolTelemetry = hikariPool.connectionPoolTelemetry
    if (poolTelemetry != null) {
      poolTelemetry.instruments.openConnections.increment()
    }
  }
}

object HikariPoolCloseConnectionMethodAdvice {

  @Advice.OnMethodExit
  def exit(@This hikariPool: Object): Unit = {
    hikariPool.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry.instruments.openConnections.decrement()
  }
}

object HikariPoolCreateTimeoutExceptionMethodAdvice {

  @Advice.OnMethodExit
  def exit(@This hikariPool: Object): Unit =
    hikariPool.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry.instruments.borrowTimeouts.increment()
}

object ProxyConnectionCloseMethodAdvice {

  @Advice.OnMethodExit
  def exit(@This proxyConnection: Object): Unit = {
    proxyConnection.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry.instruments.borrowedConnections.decrement()
  }
}

object ProxyConnectionStatementMethodsAdvice {

  @Advice.OnMethodExit
  def exit(@This proxyConnection: Object, @Return statement: Statement): Unit = {
    val poolTracker = proxyConnection.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry

    statement
      .unwrap(classOf[Statement])
      .asInstanceOf[HasConnectionPoolTelemetry]
      .setConnectionPoolTelemetry(poolTracker)

  }
}

object HikariPoolGetConnectionAdvice {

  @Advice.OnMethodEnter
  def executeStart(): Long = {
    System.nanoTime()
  }

  @Advice.OnMethodExit(onThrowable = classOf[Exception])
  def executeEnd(@Advice.Enter startTime: Long, @Advice.Return connection: Connection, @Advice.This pool: HasConnectionPoolTelemetry,
      @Advice.Thrown throwable: java.lang.Throwable): Unit = {

    val borrowTime = System.nanoTime() - startTime
    val poolMetrics = pool.connectionPoolTelemetry

    poolMetrics.instruments.borrowTime.record(borrowTime)

    if(throwable == null && connection != null) {
      poolMetrics.instruments.borrowedConnections.increment()
      connection
        .asInstanceOf[HasConnectionPoolTelemetry]
        .setConnectionPoolTelemetry(poolMetrics)
    }
  }
}

object PoolBaseNewConnectionAdvice {
  import Hooks.PreStart

  @Advice.OnMethodEnter
  def enter(@Advice.This pool: Any, @Advice.Argument(0) connection: Any): Scope = {
    connection.asInstanceOf[HasConnectionPoolTelemetry].setConnectionPoolTelemetry(pool.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry)
    Kamon.store(Kamon.currentContext().withKey(PreStart.Key, PreStart.updateOperationName("connection-init")))
  }

  @Advice.OnMethodExit
  def exit(@Advice.Enter scope: Scope): Unit =
    scope.close()
}