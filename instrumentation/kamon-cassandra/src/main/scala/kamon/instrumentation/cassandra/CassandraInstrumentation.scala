/*
 *  ==========================================================================================
 *  Copyright © 2013-2020 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon.instrumentation.cassandra

import com.datastax.driver.core.Host
import com.typesafe.config.Config
import kamon.Kamon
import kamon.instrumentation.trace.SpanTagger.TagMode

import scala.concurrent.duration.Duration

object CassandraInstrumentation {

  private val UnknownTargetTagValue = "unknown"

  @volatile var settings: Settings = readSettings(Kamon.config())
  Kamon.onReconfigure(newConfig => settings = readSettings(newConfig))

  def createNode(host: Host): Node = {
    Node(
      host.getAddress.getHostAddress,
      Option(host.getDatacenter).getOrElse(UnknownTargetTagValue),
      Option(host.getRack).getOrElse(UnknownTargetTagValue)
    )
  }

  private def readSettings(config: Config) = {
    val cassandraConfig = config.getConfig("kamon.instrumentation.cassandra")

    Settings(
      sampleInterval                 = Duration.fromNanos(cassandraConfig.getDuration("metrics.sample-interval").toNanos),
      trackHostConnectionPoolMetrics = cassandraConfig.getBoolean("metrics.track-host-connection-pool-metrics"),
      nodeTagMode                    = TagMode.from(cassandraConfig.getString("tracing.tags.node")),
      rackTagMode                    = TagMode.from(cassandraConfig.getString("tracing.tags.rack")),
      dcTagMode                      = TagMode.from(cassandraConfig.getString("tracing.tags.dc")),
      traceExecutions                = cassandraConfig.getBoolean("tracing.enabled")
    )
  }

  case class Node(address: String, dc: String, rack: String)

  case class Settings(
      sampleInterval:                 Duration,
      trackHostConnectionPoolMetrics: Boolean,
      nodeTagMode:                    TagMode,
      rackTagMode:                    TagMode,
      dcTagMode:                      TagMode,
      traceExecutions:                Boolean
  )

  object Tags {
    val ErrorSource              = "source"
    val DC                       = "cassandra.dc"
    val Node                     = "cassandra.node"
    val Rack                     = "cassandra.rack"
    val CassandraDriverComponent = "cassandra.driver"
  }
}
