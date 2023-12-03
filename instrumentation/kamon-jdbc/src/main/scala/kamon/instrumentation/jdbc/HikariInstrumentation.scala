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

import java.sql.{Connection, Statement}
import java.util.concurrent.atomic.AtomicReference
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.pool.{HikariPool, PoolEntry, PoolEntryProtectedAccess}
import kamon.Kamon
import kamon.context.Storage.Scope
import kamon.instrumentation.jdbc.JdbcMetrics.ConnectionPoolInstruments
import kamon.tag.TagSet
import kamon.trace.Hooks
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice._

import scala.annotation.static
import scala.util.Try

class HikariInstrumentation extends InstrumentationBuilder {

  onType("com.zaxxer.hikari.pool.HikariPool")
    .mixin(classOf[HasConnectionPoolTelemetry.Mixin])
    .advise(isConstructor(), classOf[HikariPoolConstructorAdvice])
    .advise(method("checkFailFast"), classOf[CheckFailFastAdvice])
    .advise(method("shutdown"), classOf[HikariPoolShutdownMethodAdvice])
    .advise(method("createPoolEntry"), classOf[HikariPoolCreatePoolEntryMethodAdvice])
    .advise(method("closeConnection"), classOf[HikariPoolCloseConnectionMethodAdvice])
    .advise(method("createTimeoutException"), classOf[HikariPoolCreateTimeoutExceptionMethodAdvice])
    .advise(method("getConnection").and(takesArguments(0)), classOf[HikariPoolGetConnectionAdvice])

  onType("com.zaxxer.hikari.pool.PoolBase")
    .advise(method("setupConnection"), classOf[PoolBaseNewConnectionAdvice])

  onType("com.zaxxer.hikari.pool.PoolEntry")
    .mixin(classOf[HasConnectionPoolTelemetry.Mixin])
    .advise(method("createProxyConnection"), classOf[CreateProxyConnectionAdvice])

  onType("com.zaxxer.hikari.pool.ProxyConnection")
    .advise(method("close"), classOf[ProxyConnectionCloseMethodAdvice])
    .advise(method("createStatement"), classOf[ProxyConnectionStatementMethodsAdvice])
    .advise(method("prepareStatement"), classOf[ProxyConnectionStatementMethodsAdvice])
    .advise(method("prepareCall"), classOf[ProxyConnectionStatementMethodsAdvice])
}

case class ConnectionPoolTelemetry(instruments: ConnectionPoolInstruments, databaseTags: DatabaseTags)

trait HasConnectionPoolTelemetry {
  def connectionPoolTelemetry: AtomicReference[ConnectionPoolTelemetry]
  def setConnectionPoolTelemetry(cpTelemetry: AtomicReference[ConnectionPoolTelemetry]): Unit
}

object HasConnectionPoolTelemetry {

  class Mixin(var connectionPoolTelemetry: AtomicReference[ConnectionPoolTelemetry]) extends HasConnectionPoolTelemetry {
    override def setConnectionPoolTelemetry(cpTelemetry: AtomicReference[ConnectionPoolTelemetry]): Unit =
      this.connectionPoolTelemetry = cpTelemetry
  }
}

class CheckFailFastAdvice
object CheckFailFastAdvice {

  @Advice.OnMethodEnter
  @static def enter(@Advice.This hikariPool: Any): Unit = {
    hikariPool.asInstanceOf[HasConnectionPoolTelemetry].setConnectionPoolTelemetry(new AtomicReference[ConnectionPoolTelemetry]())
  }
}

class HikariPoolConstructorAdvice
object HikariPoolConstructorAdvice {

  @Advice.OnMethodExit
  @static def exit(@Advice.This hikariPool: HasConnectionPoolTelemetry, @Advice.Argument(0) config: HikariConfig): Unit = {
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
    hikariPool.connectionPoolTelemetry.set(ConnectionPoolTelemetry(poolInstruments, DatabaseTags(metricTags, spanTags)))
  }
}

class HikariPoolShutdownMethodAdvice
object HikariPoolShutdownMethodAdvice {

  @Advice.OnMethodExit
  @static def exit(@This hikariPool: Object): Unit =
    hikariPool.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry.get.instruments.remove()
}

class HikariPoolCreatePoolEntryMethodAdvice
object HikariPoolCreatePoolEntryMethodAdvice {

  @Advice.OnMethodExit
  @static def exit(@This hikariPool: HasConnectionPoolTelemetry, @Advice.Return poolEntry: Any): Unit = {
    if(hikariPool != null && poolEntry != null) {
      poolEntry.asInstanceOf[HasConnectionPoolTelemetry].setConnectionPoolTelemetry(hikariPool.connectionPoolTelemetry)

      val poolTelemetry = hikariPool.connectionPoolTelemetry.get
      if (poolTelemetry != null) {
        poolTelemetry.instruments.openConnections.increment()
      }
    }
  }
}

class HikariPoolCloseConnectionMethodAdvice
object HikariPoolCloseConnectionMethodAdvice {

  @Advice.OnMethodExit
  @static def exit(@This hikariPool: Any): Unit = {
    hikariPool.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry.get.instruments.openConnections.decrement()
  }
}

class HikariPoolCreateTimeoutExceptionMethodAdvice
object HikariPoolCreateTimeoutExceptionMethodAdvice {

  @Advice.OnMethodExit
  @static def exit(@This hikariPool: Any): Unit =
    hikariPool.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry.get.instruments.borrowTimeouts.increment()
}

class ProxyConnectionCloseMethodAdvice
object ProxyConnectionCloseMethodAdvice {

  @Advice.OnMethodExit
  @static def exit(@This proxyConnection: Any): Unit = {
    proxyConnection.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry.get.instruments.borrowedConnections.decrement()
  }
}

class ProxyConnectionStatementMethodsAdvice
object ProxyConnectionStatementMethodsAdvice {

  @Advice.OnMethodExit
  @static def exit(@This proxyConnection: Any, @Return statement: Statement): Unit = {
    val poolTracker = proxyConnection.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry

    statement
      .unwrap(classOf[Statement])
      .asInstanceOf[HasConnectionPoolTelemetry]
      .setConnectionPoolTelemetry(poolTracker)

  }
}

class HikariPoolGetConnectionAdvice
object HikariPoolGetConnectionAdvice {

  @Advice.OnMethodEnter
  @static def executeStart(): Long = {
    System.nanoTime()
  }

  @Advice.OnMethodExit(onThrowable = classOf[Exception])
  @static def executeEnd(@Advice.Enter startTime: Long, @Advice.Return connection: Connection, @Advice.This pool: HasConnectionPoolTelemetry,
      @Advice.Thrown throwable: java.lang.Throwable): Unit = {

    val borrowTime = System.nanoTime() - startTime
    val poolMetrics = pool.connectionPoolTelemetry.get

    poolMetrics.instruments.borrowTime.record(borrowTime)

    if(throwable == null && connection != null) {
      poolMetrics.instruments.borrowedConnections.increment()
      connection
        .asInstanceOf[HasConnectionPoolTelemetry]
        .setConnectionPoolTelemetry(pool.connectionPoolTelemetry)
    }
  }
}

class PoolBaseNewConnectionAdvice
object PoolBaseNewConnectionAdvice {
  import Hooks.PreStart

  @Advice.OnMethodEnter
  @static def enter(@Advice.This pool: Any, @Advice.Argument(0) connection: Any): Scope = {
    connection.asInstanceOf[HasConnectionPoolTelemetry].setConnectionPoolTelemetry(pool.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry)
    Kamon.storeContext(Kamon.currentContext().withEntry(PreStart.Key, PreStart.updateOperationName("init")))
  }

  @Advice.OnMethodExit
  @static def exit(@Advice.Enter scope: Scope): Unit = {
    scope.close()
  }
}

class CreateProxyConnectionAdvice
object CreateProxyConnectionAdvice {

  @Advice.OnMethodExit
  @static def exit(@Advice.This poolEntry: Any): Unit = {
    val realConnection = PoolEntryProtectedAccess.underlyingConnection(poolEntry)
    if(realConnection != null) {
      realConnection.asInstanceOf[HasConnectionPoolTelemetry].setConnectionPoolTelemetry(poolEntry.asInstanceOf[HasConnectionPoolTelemetry].connectionPoolTelemetry)
    }
  }
}
