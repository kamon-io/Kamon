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

package kamon.instrumentation.jdbc

import kamon.instrumentation.jdbc.advisor._
import kamon.instrumentation.jdbc.mixin.HasConnectionPoolMetricsMixin
import kanela.agent.api.instrumentation.InstrumentationBuilder

class StatementInstrumentation extends InstrumentationBuilder {

  private val withOneStringArgument = withArgument(0, classOf[String])

  onSubTypesOf("java.sql.Statement")
    .mixin(classOf[HasConnectionPoolMetricsMixin])
    .advise(method("execute").and(withOneStringArgument), classOf[StatementExecuteMethodAdvisor])
    .advise(method("executeQuery").and(withOneStringArgument), classOf[StatementExecuteQueryMethodAdvisor])
    .advise(method("executeUpdate").and(withOneStringArgument), classOf[StatementExecuteUpdateMethodAdvisor])
    .advise(anyMethods("executeBatch", "executeLargeBatch"), classOf[StatementExecuteBatchMethodAdvisor])

  onSubTypesOf("java.sql.PreparedStatement")
    .advise(method("execute"), classOf[PreparedStatementExecuteMethodAdvisor])
    .advise(method("executeQuery"), classOf[PreparedStatementExecuteQueryMethodAdvisor])
    .advise(method("executeUpdate"), classOf[PreparedStatementExecuteUpdateMethodAdvisor])
}

