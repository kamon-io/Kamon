/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

import kamon.jdbc.instrumentation.advisor._
import kamon.jdbc.instrumentation.mixin.HasConnectionPoolMetricsMixin
import kanela.agent.scala.KanelaInstrumentation


class StatementInstrumentation extends KanelaInstrumentation {

  /**
    * Instrument:
    *
    * java.sql.Statement::execute
    * java.sql.Statement::executeQuery
    * java.sql.Statement::executeUpdate
    * java.sql.Statement::executeBatch
    * java.sql.Statement::executeLargeBatch
    *
    * Mix:
    *
    * java.sql.Statement with kamon.jdbc.instrumentation.mixin.HasConnectionPoolMetrics
    *
    */
  private val withOneStringArgument = withArgument(0, classOf[String])

  forSubtypeOf("java.sql.Statement") { builder =>
    builder
      .withMixin(classOf[HasConnectionPoolMetricsMixin])
      .withAdvisorFor(method("execute").and(withOneStringArgument), classOf[StatementExecuteMethodAdvisor])
      .withAdvisorFor(method("executeQuery").and(withOneStringArgument), classOf[StatementExecuteQueryMethodAdvisor])
      .withAdvisorFor(method("executeUpdate").and(withOneStringArgument), classOf[StatementExecuteUpdateMethodAdvisor])
      .withAdvisorFor(anyMethod("executeBatch", "executeLargeBatch"), classOf[StatementExecuteBatchMethodAdvisor])
      .build()
  }


  /**
    * Instrument:
    *
    * java.sql.PreparedStatement::execute
    * java.sql.PreparedStatement::executeQuery
    * java.sql.PreparedStatement::executeUpdate
    * java.sql.PreparedStatement::executeBatch
    * java.sql.PreparedStatement::executeLargeBatch
    *
    */
  forSubtypeOf("java.sql.PreparedStatement") { builder =>
    builder
      .withAdvisorFor(method("execute"), classOf[PreparedStatementExecuteMethodAdvisor])
      .withAdvisorFor(method("executeQuery"), classOf[PreparedStatementExecuteQueryMethodAdvisor])
      .withAdvisorFor(method("executeUpdate"), classOf[PreparedStatementExecuteUpdateMethodAdvisor])
      .build()
  }
}

