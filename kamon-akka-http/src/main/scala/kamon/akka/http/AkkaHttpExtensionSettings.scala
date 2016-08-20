/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.akka.http

import akka.actor.ReflectiveDynamicAccess
import com.typesafe.config.Config

case class AkkaHttpExtensionSettings(includeTraceTokenHeader: Boolean,
  traceTokenHeaderName: String,
  nameGenerator: NameGenerator,
  clientInstrumentationLevel: ClientInstrumentationLevel.Level)

object AkkaHttpExtensionSettings {
  def apply(config: Config): AkkaHttpExtensionSettings = {
    val akkaHttpConfig = config.getConfig("kamon.akka-http")

    val includeTraceTokenHeader: Boolean = akkaHttpConfig.getBoolean("automatic-trace-token-propagation")
    val traceTokenHeaderName: String = akkaHttpConfig.getString("trace-token-header-name")

    val nameGeneratorFQN = akkaHttpConfig.getString("name-generator")
    val nameGenerator: NameGenerator = new ReflectiveDynamicAccess(getClass.getClassLoader)
      .createInstanceFor[NameGenerator](nameGeneratorFQN, Nil).get // let's bubble up any problems.

    val clientInstrumentationLevel: ClientInstrumentationLevel.Level = akkaHttpConfig.getString("client.instrumentation-level") match {
      case "request-level" ⇒ ClientInstrumentationLevel.RequestLevelAPI
      case "host-level"    ⇒ ClientInstrumentationLevel.HostLevelAPI
      case other           ⇒ sys.error(s"Invalid client instrumentation level [$other] found in configuration.")
    }

    AkkaHttpExtensionSettings(includeTraceTokenHeader, traceTokenHeaderName, nameGenerator, clientInstrumentationLevel)
  }
}

object ClientInstrumentationLevel {
  sealed trait Level
  case object RequestLevelAPI extends Level
  case object HostLevelAPI extends Level
}
