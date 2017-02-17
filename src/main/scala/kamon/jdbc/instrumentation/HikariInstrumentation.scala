package kamon.jdbc.instrumentation

import java.sql.{ PreparedStatement, Statement }

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.pool.{ HikariPool, HikariProxyConnection, ProxyConnection }
import kamon.Kamon
import kamon.jdbc.metric.ConnectionPoolMetrics
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class HikariInstrumentation {
  val HikariMetricCategory = "hikari-pool"

  @DeclareMixin("com.zaxxer.hikari.pool.HikariPool")
  def mixinHasConnectionPoolTrackerToHikariDataSource: HasConnectionPoolMetrics.Mixin = HasConnectionPoolMetrics()

  @DeclareMixin("com.zaxxer.hikari.pool.ProxyConnection")
  def mixinHasConnectionPoolTrackerToProxyConnection: HasConnectionPoolMetrics.Mixin = HasConnectionPoolMetrics()

  @Pointcut("execution(com.zaxxer.hikari.pool.HikariPool.new(..)) && this(hikariPool) && args(config)")
  def hikariPoolConstructor(hikariPool: HikariPool, config: HikariConfig): Unit = {}

  @Around("hikariPoolConstructor(hikariPool, config)")
  def afterReturningHikariPoolConstructor(pjp: ProceedingJoinPoint, hikariPool: HikariPool, config: HikariConfig): Unit = {
    hikariPool.asInstanceOf[HasConnectionPoolMetrics.Mixin].setConnectionPoolMetrics(
      ConnectionPoolMetrics.create(HikariMetricCategory, config.getPoolName)
    )
    pjp.proceed()
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.HikariPool.shutdown()) && this(hikariPool)")
  def hikariPoolShutdown(hikariPool: HikariPool): Unit = {}

  @AfterReturning("hikariPoolShutdown(hikariPool)")
  def afterHikariPoolShutdown(hikariPool: HikariPool): Unit = {
    Kamon.metrics.removeEntity(hikariPool.toString, HikariMetricCategory)
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.HikariPool.createPoolEntry()) && this(hikariPool)")
  def createPoolEntry(hikariPool: HikariPool): Unit = {}

  @AfterReturning("createPoolEntry(hikariPool)")
  def afterCreatePoolEntry(hikariPool: HikariPool): Unit = {
    hikariPool.asInstanceOf[HasConnectionPoolMetrics.Mixin].connectionPoolMetrics.openConnections.increment()
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.HikariPool.closeConnection(..)) && this(hikariPool)")
  def closeConnection(hikariPool: HikariPool): Unit = {}

  @After("closeConnection(hikariPool)")
  def afterCloseConnection(hikariPool: HikariPool): Unit = {
    hikariPool.asInstanceOf[HasConnectionPoolMetrics.Mixin].connectionPoolMetrics.openConnections.decrement()
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.HikariPool.getConnection(*)) && this(hikariPool)")
  def hikariPoolGetConnection(hikariPool: HikariPool): Unit = {}

  @AfterReturning(value = "hikariPoolGetConnection(hikariPool)", returning = "proxyConnection")
  def aroundHikariPoolGetConnection(hikariPool: HikariPool, proxyConnection: ProxyConnection): Unit = {
    val poolMetrics = hikariPool.asInstanceOf[HasConnectionPoolMetrics.Mixin].connectionPoolMetrics
    poolMetrics.borrowedConnections.increment()

    proxyConnection.asInstanceOf[HasConnectionPoolMetrics.Mixin].setConnectionPoolMetrics(poolMetrics)
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.ProxyConnection.close()) && this(proxyConnection)")
  def returnBorrowedConnection(proxyConnection: ProxyConnection): Unit = {}

  @After("returnBorrowedConnection(proxyConnection)")
  def afterReturnBorrowedConnection(proxyConnection: ProxyConnection): Unit = {
    proxyConnection.asInstanceOf[HasConnectionPoolMetrics.Mixin].connectionPoolMetrics.borrowedConnections.decrement()
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.ProxyConnection.prepareStatement(..)) && this(proxyConnection)")
  def prepareStatementInConnection(proxyConnection: ProxyConnection): Unit = {}

  @Pointcut("execution(* com.zaxxer.hikari.pool.ProxyConnection.createStatement(..)) && this(proxyConnection)")
  def createStatementInConnection(proxyConnection: ProxyConnection): Unit = {}

  @AfterReturning(value = "prepareStatementInConnection(proxyConnection) || createStatementInConnection(proxyConnection)", returning = "statement")
  def afterCreateStatementIntConnection(proxyConnection: ProxyConnection, statement: Statement): Unit = {
    val poolTracker = proxyConnection.asInstanceOf[HasConnectionPoolMetrics.Mixin].connectionPoolMetrics

    statement
      .unwrap(classOf[Statement])
      .asInstanceOf[HasStatementMetrics.Mixin]
      .setStatementMetrics(poolTracker)
  }
}

