package kamon.jdbc.instrumentation

import java.sql.Statement

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.pool.{HikariPool, ProxyConnection}
import kamon.jdbc.Metrics
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class HikariInstrumentation {
  val HikariMetricCategory = "hikari-pool"

  @DeclareMixin("com.zaxxer.hikari.pool.HikariPool")
  def mixinHasConnectionPoolTrackerToHikariDataSource: Mixin.HasConnectionPoolMetrics = Mixin.HasConnectionPoolMetrics()

  @DeclareMixin("com.zaxxer.hikari.pool.ProxyConnection")
  def mixinHasConnectionPoolTrackerToProxyConnection: Mixin.HasConnectionPoolMetrics = Mixin.HasConnectionPoolMetrics()

  @Pointcut("execution(com.zaxxer.hikari.pool.HikariPool.new(..)) && this(hikariPool) && args(config)")
  def hikariPoolConstructor(hikariPool: HikariPool, config: HikariConfig): Unit = {}

  @Around("hikariPoolConstructor(hikariPool, config)")
  def afterReturningHikariPoolConstructor(pjp: ProceedingJoinPoint, hikariPool: HikariPool, config: HikariConfig): Unit = {
    hikariPool.asInstanceOf[Mixin.HasConnectionPoolMetrics].setConnectionPoolMetrics(
      Metrics.ConnectionPoolMetrics(
        Map(
          "poolVendor" -> "hikari",
          "poolName" -> config.getPoolName
        )
      )
    )

    pjp.proceed()
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.HikariPool.shutdown()) && this(hikariPool)")
  def hikariPoolShutdown(hikariPool: HikariPool): Unit = {}

  @AfterReturning("hikariPoolShutdown(hikariPool)")
  def afterHikariPoolShutdown(hikariPool: HikariPool): Unit = {
    hikariPool.asInstanceOf[Mixin.HasConnectionPoolMetrics].connectionPoolMetrics.cleanup()
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.HikariPool.createPoolEntry()) && this(hikariPool)")
  def createPoolEntry(hikariPool: HikariPool): Unit = {}

  @AfterReturning("createPoolEntry(hikariPool)")
  def afterCreatePoolEntry(hikariPool: HikariPool): Unit = {
    hikariPool.asInstanceOf[Mixin.HasConnectionPoolMetrics].connectionPoolMetrics.openConnections.increment()
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.HikariPool.closeConnection(..)) && this(hikariPool)")
  def closeConnection(hikariPool: HikariPool): Unit = {}

  @After("closeConnection(hikariPool)")
  def afterCloseConnection(hikariPool: HikariPool): Unit = {
    hikariPool.asInstanceOf[Mixin.HasConnectionPoolMetrics].connectionPoolMetrics.openConnections.decrement()
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.HikariPool.getConnection(*)) && this(hikariPool)")
  def hikariPoolGetConnection(hikariPool: HikariPool): Unit = {}

  @Around("hikariPoolGetConnection(hikariPool)")
  def aroundHikariPoolGetConnection(pjp: ProceedingJoinPoint, hikariPool: HikariPool): Any = {
    val poolMetrics = hikariPool.asInstanceOf[Mixin.HasConnectionPoolMetrics].connectionPoolMetrics
    val startTime = System.nanoTime()
    var connection: Any = null

    try {
      connection = pjp.proceed()
      poolMetrics.borrowedConnections.increment()

      connection
        .asInstanceOf[Mixin.HasConnectionPoolMetrics]
        .setConnectionPoolMetrics(poolMetrics)

    } finally {
      poolMetrics.borrowTime.record(System.nanoTime() - startTime)
    }

    connection
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.HikariPool.createTimeoutException(..)) && this(hikariPool)")
  def createTimeoutException(hikariPool: HikariPool): Unit = {}

  @After("createTimeoutException(hikariPool)")
  def afterCreateTimeoutException(hikariPool: HikariPool): Unit = {
    hikariPool.asInstanceOf[Mixin.HasConnectionPoolMetrics].connectionPoolMetrics.borrowTimeouts.increment()
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.ProxyConnection.close()) && this(proxyConnection)")
  def returnBorrowedConnection(proxyConnection: ProxyConnection): Unit = {}

  @After("returnBorrowedConnection(proxyConnection)")
  def afterReturnBorrowedConnection(proxyConnection: ProxyConnection): Unit = {
    proxyConnection.asInstanceOf[Mixin.HasConnectionPoolMetrics].connectionPoolMetrics.borrowedConnections.decrement()
  }

  @Pointcut("execution(* com.zaxxer.hikari.pool.ProxyConnection.prepareStatement(..)) && this(proxyConnection)")
  def prepareStatementInConnection(proxyConnection: ProxyConnection): Unit = {}

  @Pointcut("execution(* com.zaxxer.hikari.pool.ProxyConnection.createStatement(..)) && this(proxyConnection)")
  def createStatementInConnection(proxyConnection: ProxyConnection): Unit = {}

  @AfterReturning(value = "prepareStatementInConnection(proxyConnection) || createStatementInConnection(proxyConnection)", returning = "statement")
  def afterCreateStatementIntConnection(proxyConnection: ProxyConnection, statement: Statement): Unit = {
    val poolTracker = proxyConnection.asInstanceOf[Mixin.HasConnectionPoolMetrics].connectionPoolMetrics

    statement
      .unwrap(classOf[Statement])
      .asInstanceOf[Mixin.HasConnectionPoolMetrics]
      .setConnectionPoolMetrics(poolTracker)
  }
}

