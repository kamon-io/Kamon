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
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.scala.KanelaInstrumentation


class StatementInstrumentation extends InstrumentationBuilder {

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

  onSubTypesOf("java.sql.Statement")
      .mixin(classOf[HasConnectionPoolMetricsMixin])
      .advise(method("execute").and(withOneStringArgument), classOf[StatementExecuteMethodAdvisor])
      .advise(method("executeQuery").and(withOneStringArgument), classOf[StatementExecuteQueryMethodAdvisor])
      .advise(method("executeUpdate").and(withOneStringArgument), classOf[StatementExecuteUpdateMethodAdvisor])
      .advise(anyMethods("executeBatch", "executeLargeBatch"), classOf[StatementExecuteBatchMethodAdvisor])


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
  onSubTypesOf("java.sql.PreparedStatement")
    .advise(method("execute"), classOf[PreparedStatementExecuteMethodAdvisor])
    .advise(method("executeQuery"), classOf[PreparedStatementExecuteQueryMethodAdvisor])
    .advise(method("executeUpdate"), classOf[PreparedStatementExecuteUpdateMethodAdvisor])
}

