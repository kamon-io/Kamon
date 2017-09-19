package kamon.jdbc.instrumentation

import java.util.concurrent.Callable

import kamon.agent.libs.net.bytebuddy.implementation.bind.annotation.{RuntimeType, SuperCall, This}
import kamon.agent.scala.KamonInstrumentation
import org.mariadb.jdbc.MariaDbServerPreparedStatement
import org.mariadb.jdbc.internal.queryresults.resultset.MariaSelectResultSet

import scala.util.Try

class MariaPreparedStatementServerInstrumentation extends KamonInstrumentation {

  forTargetType("org.mariadb.jdbc.MariaDbServerPreparedStatement") { builder =>
    builder
      .withInterceptorFor(method("executeQuery"), ExecuteQueryMethodInterceptor)
      .withInterceptorFor(method("executeUpdate"), ExecuteUpdateMethodInterceptor)
      .build
  }

  override def isActive: Boolean =
    Try(Class.forName("org.mariadb.jdbc.MariaDbServerPreparedStatement", false, this.getClass.getClassLoader)).isSuccess


  object ExecuteQueryMethodInterceptor {
    import MariaPreparedStatementServerInstrumentationMethods._

    @RuntimeType
    def execute(@SuperCall callable: Callable[_], @This preparedStatement:Object): Any = {
      val pps = preparedStatement.asInstanceOf[MariaDbServerPreparedStatement]

      if(executeInternal(pps))
        pps.getResultSet
      else
        MariaSelectResultSet.createEmptyResultSet
    }
  }

  object ExecuteUpdateMethodInterceptor {
    import MariaPreparedStatementServerInstrumentationMethods._

    @RuntimeType
    def executeUpdate(@SuperCall callable: Callable[_], @This preparedStatement:Object): Any = {
      val pps = preparedStatement.asInstanceOf[MariaDbServerPreparedStatement]
      executeInternal(pps)
      pps.getUpdateCount
    }
  }

  object MariaPreparedStatementServerInstrumentationMethods {
    import java.lang.invoke.{MethodHandles, MethodType}

    private val lookup = MethodHandles.lookup
    private val executeInternalMethodType = MethodType.methodType(classOf[Boolean], classOf[Int], classOf[Boolean])

    private lazy val executeInternal =
      lookup.findVirtual(classOf[MariaDbServerPreparedStatement], "executeInternal", executeInternalMethodType)

    def executeInternal(instance:MariaDbServerPreparedStatement): Boolean =
      executeInternal.invokeExact(instance.getFetchSize, false)
  }
}


