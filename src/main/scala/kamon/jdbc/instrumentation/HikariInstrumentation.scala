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
import java.sql.{Connection, SQLException, Statement}
import java.util.concurrent.Callable

import com.zaxxer.hikari.HikariConfig
import kamon.jdbc.Metrics
import kamon.jdbc.instrumentation.mixin.{HasConnectionPoolMetrics, HasConnectionPoolMetricsMixin}
import kamon.tag.TagSet
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice
import kanela.agent.libs.net.bytebuddy.asm.Advice._
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation
import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{RuntimeType, SuperCall}

class HikariInstrumentation extends InstrumentationBuilder {

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
  onType("com.zaxxer.hikari.pool.HikariPool")
    .mixin(classOf[HasConnectionPoolMetricsMixin])
    .advise(isConstructor(), classOf[HikariPoolConstructorAdvisor])
    .advise(method("shutdown"), classOf[HikariPoolShutdownMethodAdvisor])
    .advise(method("createPoolEntry"), classOf[HikariPoolCreatePoolEntryMethodAdvisor])
    .advise(method("closeConnection"), classOf[HikariPoolCloseConnectionMethodAdvisor])
    .advise(method("createTimeoutException"), classOf[HikariPoolCreateTimeoutExceptionMethodAdvisor])
    .advise(method("getConnection").and(takesArguments(0)), classOf[HikariPoolGetConnectionMethodInterceptor])

  //class kanela.agent.api.instrumentation.InstrumentationBuilder$Target$$Lambda$111 is not visible to
  //com.zaxxer.hikari.pool.HikariPool. Class loader: sun.misc.Launcher$AppClassLoader
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
  onType("com.zaxxer.hikari.pool.ProxyConnection")
    .mixin(classOf[HasConnectionPoolMetricsMixin])
    .advise(method("close"), classOf[ProxyConnectionCloseMethodAdvisor])
    .advise(method("prepareStatement"), classOf[ProxyConnectionStatementMethodsAdvisor])
    .advise(method("createStatement"), classOf[ProxyConnectionStatementMethodsAdvisor])
}

/**
  * Advisor com.zaxxer.hikari.pool.HikariPool::new
  */
class HikariPoolConstructorAdvisor
object HikariPoolConstructorAdvisor {
  @OnMethodExit
  def onExit(@Advice.This hikariPool:HasConnectionPoolMetrics, @Advice.Argument(0) config:HikariConfig): Unit = {
    hikariPool.setConnectionPoolMetrics(
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
  def onExit(@This hikariPool: HasConnectionPoolMetrics): Unit = {
    val poolMetrics = hikariPool.connectionPoolMetrics
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
  def onExit(@This hikariPool: Object): Unit = {
    hikariPool.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics.openConnections.decrement()
  }
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
  def onExit(@This proxyConnection: Object): Unit = {
    proxyConnection.asInstanceOf[HasConnectionPoolMetrics].connectionPoolMetrics.borrowedConnections.decrement()
  }
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

  @Advice.OnMethodEnter
  def executeStart(): Long = {
    System.nanoTime()
  }

  @Advice.OnMethodExit(onThrowable = classOf[Exception])
  def executeEnd(@Advice.Enter startTime: Long, @Advice.Return connection: Connection, @Advice.This pool: HasConnectionPoolMetrics, @Advice.Thrown throwable: java.lang.Throwable): Unit = {
    val borrowTime = System.nanoTime() - startTime
    val poolMetrics = pool.connectionPoolMetrics

    poolMetrics.borrowTime.record(borrowTime)


    if(throwable == null && connection != null) {
      poolMetrics.borrowedConnections.increment()
      connection
        .asInstanceOf[HasConnectionPoolMetrics]
        .setConnectionPoolMetrics(poolMetrics)
    }
  }

}