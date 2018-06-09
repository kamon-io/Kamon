package kamon.jdbc.instrumentation.maria

import java.sql.{ResultSet, SQLException}
import java.util.concurrent.Callable

import kanela.agent.libs.net.bytebuddy.implementation.bind.annotation.{RuntimeType, SuperCall, This}
import org.mariadb.jdbc.MariaDbServerPreparedStatement
import org.mariadb.jdbc.internal.queryresults.resultset.MariaSelectResultSet

/**
  * Interceptor for org.mariadb.jdbc.MariaDbServerPreparedStatement::executeQuery
  */
object ExecuteQueryMethodInterceptor2 {
  import Methods._

  @RuntimeType
  @throws(classOf[SQLException])
  def executeQuery(@SuperCall callable: Callable[_], @This preparedStatement:Object): ResultSet = {
    val pps = preparedStatement.asInstanceOf[MariaDbServerPreparedStatement]

    if(executeInternal(pps))
      pps.getResultSet
    else
      MariaSelectResultSet.createEmptyResultSet
  }

  /**
    * Interceptor for org.mariadb.jdbc.MariaDbServerPreparedStatement::executeUpdate
    */
  object ExecuteUpdateMethodInterceptor2 {
    import Methods._

    @RuntimeType
    @throws(classOf[SQLException])
    def executeUpdate(@SuperCall callable: Callable[_], @This preparedStatement:Object): Int = {
      val pps = preparedStatement.asInstanceOf[MariaDbServerPreparedStatement]
      executeInternal(pps)
      pps.getUpdateCount
    }
  }

  object Methods {
    import java.lang.invoke.MethodHandles

    val executeInternalMethod = classOf[MariaDbServerPreparedStatement]
      .getDeclaredMethod("executeInternal", classOf[Int], classOf[Boolean])

    executeInternalMethod.setAccessible(true)

    private val executeInternal = MethodHandles.lookup.unreflect(executeInternalMethod)

    def executeInternal(instance:MariaDbServerPreparedStatement): Boolean =
      executeInternal.invokeExact(instance, instance.getFetchSize, false)
  }
}
