/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

package kamon.jdbc

import java.util.concurrent.TimeUnit.{ MILLISECONDS ⇒ milliseconds }

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import kamon.Kamon

object Jdbc extends ExtensionId[JdbcExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Jdbc
  override def createExtension(system: ExtendedActorSystem): JdbcExtension = new JdbcExtension(system)
}

class JdbcExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  private val config = system.settings.config.getConfig("kamon.jdbc")
  private val slowQueryProcessorClass = config.getString("slow-query-processor")
  private val slowQueryProcessor: SlowQueryProcessor = system.dynamicAccess.createInstanceFor[SlowQueryProcessor](slowQueryProcessorClass, Nil).get

  val slowQueryThreshold = config.getDuration("slow-query-threshold", milliseconds)

  def processSlowQuery(sql: String, executionTime: Long) = slowQueryProcessor.process(sql, executionTime, slowQueryThreshold)
}

trait SlowQueryProcessor {
  def process(sql: String, executionTime: Long, queryThreshold: Long): Unit
}

class DefaultSlowQueryProcessor extends SlowQueryProcessor {
  import org.slf4j.LoggerFactory

  val log = LoggerFactory.getLogger("slow-query-processor")

  override def process(sql: String, executionTimeInMillis: Long, queryThresholdInMillis: Long): Unit = {
    log.warn(s"The query [$sql] took $executionTimeInMillis ms and the slow query threshold is $queryThresholdInMillis ms")
  }
}
