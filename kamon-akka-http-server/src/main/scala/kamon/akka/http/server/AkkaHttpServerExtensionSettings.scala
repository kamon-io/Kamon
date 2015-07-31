/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.akka.http.server

import akka.actor.ExtendedActorSystem

case class AkkaHttpServerExtensionSettings(
  includeTraceTokenHeader: Boolean,
  traceTokenHeaderName: String,
  nameGenerator: NameGenerator)

object AkkaHttpServerExtensionSettings {
  def apply(system: ExtendedActorSystem): AkkaHttpServerExtensionSettings = {
    val config = system.settings.config.getConfig("kamon.akka.http.server")

    val includeTraceTokenHeader: Boolean = config.getBoolean("automatic-trace-token-propagation")
    val traceTokenHeaderName: String = config.getString("trace-token-header-name")

    val nameGeneratorFQN: String = config.getString("name-generator")
    val nameGenerator: NameGenerator = system.dynamicAccess.createInstanceFor[NameGenerator](nameGeneratorFQN, Nil).get

    AkkaHttpServerExtensionSettings(includeTraceTokenHeader, traceTokenHeaderName, nameGenerator)
  }
}
