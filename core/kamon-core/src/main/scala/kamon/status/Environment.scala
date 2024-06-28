/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package status

import java.net.InetAddress
import java.util.concurrent.ThreadLocalRandom
import com.typesafe.config.{Config, ConfigUtil}
import kamon.tag.TagSet
import kamon.util.HexCodec
import org.slf4j.LoggerFactory

/**
  * Describes the conditions on which the instrumented application is running. This information is typically used by
  * reporter modules to enrich the data before it is sent to external systems. Kamon will always try to create an
  * appropriate Environment instance based on the details found on the "kamon.environment" configuration settings and
  * then exposed through an instance of this class.
  *
  * Starting from a service name, Kamon will try to figure out how to create the Environment instance using:
  *
  *   - The "kamon.environment.host" setting as the hostname where this application is running. If the value "auto"
  *     (default) is present, Kamon will try to resolve the hostname automatically and use it.
  *
  *   - The "kamon.environment.instance" setting as the name of the this instance. Typically there will be several JVMs
  *     running the exact same service code on different hosts and even though they should share the same "service"
  *     name, each one should have its own identifier. If the value "auto" (default) is present, Kamon will generate an
  *     instance name with the pattern "service@host".
  *
  *   - The "kamon.environment.tags" are additional key/value pairs with information about the conditions on which this
  *     service is running. They will typically be used to include things like the Datacenter name or region, software
  *     versions and so on.
  *
  * Finally, the incarnation is a randomly generated string that uniquely identifies this JVM. The incarnation value
  * remains the same even after Kamon gets reconfigured and its host, service, instance or tags are changed.
  *
  */
case class Environment(host: String, service: String, instance: String, incarnation: String, tags: TagSet)

object Environment {

  private val _logger = LoggerFactory.getLogger(classOf[Environment])
  private val _incarnation = HexCodec.toLowerHex(ThreadLocalRandom.current().nextLong())

  /**
    * Creates an Environment instance from the provided configuration. All environment settings will be looked up on the
    * "kamon.environment" configuration path.
    */
  def from(config: Config): Environment = {
    val environmentConfig = config.getConfig("kamon.environment")
    val service = environmentConfig.getString("service")
    val tagsConfig = environmentConfig.getConfig("tags")
    val tags = flattenedTags(tagsConfig)

    val host = readValueOrGenerate(environmentConfig.getString("host"), generateHostname())
    val instance = readValueOrGenerate(environmentConfig.getString("instance"), s"$service@$host")

    Environment(host, service, instance, _incarnation, tags)
  }

  /**
    * Flattens all the configuration keys in the configuration so that we can have namespaced tag names
    * like `k8s.namespace.name` or nested configurations and they will still generate a flat `x.y.z=value`
    * set of tags.
    */
  private def flattenedTags(tagsConfig: Config): TagSet = {
    import scala.collection.JavaConverters._

    TagSet.from(
      tagsConfig.entrySet()
        .iterator()
        .asScala
        .map { e => ConfigUtil.splitPath(e.getKey).asScala.mkString(".") -> e.getValue.unwrapped().toString }
        .toMap
    )
  }

  private def generateHostname(): String = {
    try InetAddress.getLocalHost.getHostName()
    catch {
      case t: Throwable =>
        _logger.warn("Could not automatically resolve a host name for this instance, falling back to 'localhost'", t)
        "localhost"
    }
  }

  private def readValueOrGenerate(configuredValue: String, generator: => String): String =
    if (configuredValue == "auto") generator else configuredValue
}
