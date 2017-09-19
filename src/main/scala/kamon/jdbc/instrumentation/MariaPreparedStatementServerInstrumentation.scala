package kamon.jdbc.instrumentation

import kamon.agent.scala.KamonInstrumentation
import kamon.jdb.instrumentation.{ExecuteQueryMethodInterceptor, ExecuteUpdateMethodInterceptor}
import org.mariadb.jdbc.MariaDbServerPreparedStatement

import scala.util.Try

class MariaPreparedStatementServerInstrumentation extends KamonInstrumentation {

  forTargetType("org.mariadb.jdbc.MariaDbServerPreparedStatement") { builder =>
    builder
      .withAdvisorFor(method("executeQuery"), classOf[ExecuteQueryMethodInterceptor])
      .withAdvisorFor(method("executeUpdate"), classOf[ExecuteUpdateMethodInterceptor])
      .build
  }

  override def isActive: Boolean =
    Try(Class.forName("org.mariadb.jdbc.MariaDbServerPreparedStatement", false, this.getClass.getClassLoader)).isSuccess
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


