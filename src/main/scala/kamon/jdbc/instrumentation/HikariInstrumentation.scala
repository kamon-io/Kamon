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

import java.lang.reflect.Method
import java.sql.{Connection, Statement}
import java.util.concurrent.Callable

import com.zaxxer.hikari.HikariConfig
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice._
import kanela.agent.libs.net.bytebuddy.implementation.bind.{annotation, _}
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{RuntimeType, SuperCall}
import kanela.agent.scala.KanelaInstrumentation
import kamon.jdbc.Metrics
import kamon.jdbc.instrumentation.mixin.{HasConnectionPoolMetrics, HasConnectionPoolMetricsMixin}

class HikariInstrumentation extends KanelaInstrumentation {

  /**
    * Instrument:
    *
    * com.zaxxer.hikari.pool.HikariPool::new
    * com.zaxxer.hikari.pool.HikariPool::shutdown
    * com.zaxxer.hikari.pool.HikariPool::createPoolEntry
    * com.zaxxer.hikari.pool.HikariPool::closeConnection
    * com.zaxxer.hikari.pool.HikariPool::createTimeoutException
    * com.zaxxer.hikari.pool.HikariPool::getConnection
    *
    * Mix:
    *
    * com.zaxxer.hikari.pool.HikariPool with kamon.jdbc.instrumentation.mixin.HasConnectionPoolMetrics
    *
    */
  forTargetType("com.zaxxer.hikari.pool.HikariPool") { builder =>
    builder
      .withMixin(classOf[HasConnectionPoolMetricsMixin])
      .withAdvisorFor(isConstructor(), classOf[HikariPoolConstructorAdvisor])
      .withAdvisorFor(method("shutdown"), classOf[HikariPoolShutdownMethodAdvisor])
      .withAdvisorFor(method("createPoolEntry"), classOf[HikariPoolCreatePoolEntryMethodAdvisor])
      .withAdvisorFor(method("closeConnection"), classOf[HikariPoolCloseConnectionMethodAdvisor])
      .withAdvisorFor(method("createTimeoutException"), classOf[HikariPoolCreateTimeoutExceptionMethodAdvisor])
      .withInterceptorFor(method("getConnection").and(takesArguments(0)), classOf[HikariPoolGetConnectionMethodInterceptor])
      .build()
  }


  /**
    * Instrument:
    *
    * com.zaxxer.hikari.pool.ProxyConnection::close
    * com.zaxxer.hikari.pool.ProxyConnection::prepareStatement
    * com.zaxxer.hikari.pool.ProxyConnection::createStatement
    *
    * Mix:
    *
    * com.zaxxer.hikari.pool.HikariPool with kamon.jdbc.instrumentation.mixin.HasConnectionPoolMetrics
    *
    */
  forTargetType("com.zaxxer.hikari.pool.ProxyConnection") { builder =>
    builder
      .withMixin(classOf[HasConnectionPoolMetricsMixin])
      .withAdvisorFor(method("close"), classOf[ProxyConnectionCloseMethodAdvisor])
      .withAdvisorFor(method("prepareStatement"), classOf[ProxyConnectionStatementMethodsAdvisor])
      .withAdvisorFor(method("createStatement"), classOf[ProxyConnectionStatementMethodsAdvisor])
      .build()
  }
}

/**
  * Advisor com.zaxxer.hikari.pool.HikariPool::new
  */
class HikariPoolConstructorAdvisor
object HikariPoolConstructorAdvisor {
  @OnMethodExit
  def onExit(@Advice.This hikariPool:Object, @Advice.Argument(0) config:HikariConfig): Unit = {
    hikariPool.asInstanceOf[HasConnectionPoolMetrics].setConnectionPoolMetrics(
      Metrics.ConnectionPoolMetrics(
        Map(
          "poolVendor" -> "hikari",
          "poolName" -> config.getPoolName
        )
      )
    )
  }
}

/**
  * Advisor com.zaxxer.hikari.pool.HikariPool::shutdown
  */
class HikariPoolShutdownMethodAdvisor
object HikariPoolShutdownMethodAdvisor {

  @OnMethodExit
  def onExit(@This hikariPool: Object): Unit =
    hikariPool.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics.cleanup()
}

/**
  * Advisor com.zaxxer.hikari.pool.HikariPool::createPoolEntry
  */
class HikariPoolCreatePoolEntryMethodAdvisor
object HikariPoolCreatePoolEntryMethodAdvisor {

  @OnMethodExit
  def onExit(@This hikariPool: Object): Unit = {
    val poolMetrics = hikariPool.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics

    if (poolMetrics != null)
      poolMetrics.openConnections.increment()
  }
}

/**
  * Advisor com.zaxxer.hikari.pool.HikariPool::closeConnection
  */
class HikariPoolCloseConnectionMethodAdvisor
object HikariPoolCloseConnectionMethodAdvisor {

  @OnMethodExit
  def onExit(@This hikariPool: Object): Unit =
    hikariPool.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics.openConnections.decrement()
}

/**
  * Advisor com.zaxxer.hikari.pool.HikariPool::createTimeOutException
  */
class HikariPoolCreateTimeoutExceptionMethodAdvisor
object HikariPoolCreateTimeoutExceptionMethodAdvisor {

  @OnMethodExit
  def onExit(@This hikariPool: Object): Unit =
    hikariPool.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics.borrowTimeouts.increment()
}

/**
  * Advisor com.zaxxer.hikari.pool.ProxyConnection::close
  */
class ProxyConnectionCloseMethodAdvisor
object ProxyConnectionCloseMethodAdvisor {

  @OnMethodExit
  def onExit(@This proxyConnection: Object): Unit =
    proxyConnection.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics.borrowedConnections.decrement()
}


/**
  * Advisor com.zaxxer.hikari.pool.ProxyConnection::prepareStatement
  * Advisor com.zaxxer.hikari.pool.ProxyConnection::createStatement
  */
class ProxyConnectionStatementMethodsAdvisor
object ProxyConnectionStatementMethodsAdvisor {

  @OnMethodExit
  def onExit(@This proxyConnection: Object, @Return statement: Statement): Unit = {
    val poolTracker = proxyConnection.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics

    statement
      .unwrap(classOf[Statement])
      .asInstanceOf[HasConnectionPoolMetrics]
      .setConnectionPoolMetrics(poolTracker)

  }
}

/**
  * Interceptor com.zaxxer.hikari.pool.HikariPool::getConnection
  */
class HikariPoolGetConnectionMethodInterceptor
object HikariPoolGetConnectionMethodInterceptor {

  @RuntimeType
  def executeUpdate(@annotation.Origin obj:Method, @SuperCall callable: Callable[Connection], @annotation.This hikariPool: Object): Connection = {
    val poolMetrics = hikariPool.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics
    val startTime = System.nanoTime()
    var connection: Connection = null

    try {
      connection = callable.call()
      poolMetrics.borrowedConnections.increment()

      connection
        .asInstanceOf[HasConnectionPoolMetrics]
        .setConnectionPoolMetrics(poolMetrics)

    } finally {
      poolMetrics.borrowTime.record(System.nanoTime() - startTime)
    }
    connection
  }
}