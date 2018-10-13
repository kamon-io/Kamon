/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon

import java.net.InetAddress
import java.util.concurrent.ThreadLocalRandom

import com.typesafe.config.Config
import kamon.util.HexCodec




case class Environment(host: String, service: String, instance: String, incarnation: String, tags: Map[String, String])

object Environment {
  private val incarnation = HexCodec.toLowerHex(ThreadLocalRandom.current().nextLong())

  def fromConfig(config: Config): Environment = {
    val environmentConfig = config.getConfig("kamon.environment")
    val service = environmentConfig.getString("service")
    val tagsConfig = environmentConfig.getConfig("tags")
    val tags = tagsConfig.topLevelKeys.map(tag => (tag -> tagsConfig.getString(tag))).toMap

    val host = readValueOrGenerate(
      environmentConfig.getString("host"),
      InetAddress.getLocalHost.getHostName
    )

    val instance = readValueOrGenerate(
      environmentConfig.getString("instance"),
      s"$service@$host"
    )

    Environment(host, service, instance, incarnation, tags)
  }

  private def readValueOrGenerate(configuredValue: String, generator: => String): String =
    if(configuredValue == "auto") generator else configuredValue
}
