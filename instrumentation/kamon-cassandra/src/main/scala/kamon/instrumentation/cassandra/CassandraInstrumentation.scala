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
import kamon.Configuration.OnReconfigureHook
import kamon.Kamon
import kamon.instrumentation.cassandra.metrics.NodeMonitor
import kamon.instrumentation.trace.SpanTagger
import kamon.instrumentation.trace.SpanTagger.TagMode
import kamon.tag.TagSet
import kamon.trace.Span

import scala.concurrent.duration.Duration

object CassandraInstrumentation {

  private val UnknownTargetTagValue = "unknown"
  @volatile var settings: Settings = readSettings(Kamon.config())
  Kamon.onReconfigure(newConfig => settings = readSettings(newConfig))

  def createNode(host: Host, cluster: String): Node = {
    Node(
      host.getAddress.getHostAddress,
      Option(host.getDatacenter).getOrElse(UnknownTargetTagValue),
      Option(host.getRack).getOrElse(UnknownTargetTagValue),
      cluster
    )
  }

  def createNodeTags(node: Node): TagSet =
    TagSet.from(
      Map(
        Tags.Host    -> node.address,
        Tags.DC      -> node.dc,
        Tags.Rack    -> node.rack,
        Tags.Cluster -> node.cluster
      )
    )

  def tagSpanWithNode(node: Node, span: Span): Unit = {
    SpanTagger.tag(span, Tags.Host, node.address, settings.hostTagMode)
    SpanTagger.tag(span, Tags.DC, node.dc, settings.dcTagMode)
    SpanTagger.tag(span, Tags.Rack, node.rack, settings.rackTagMode)
    SpanTagger.tag(span, Tags.Cluster, node.cluster, settings.clusterTagMode)
  }

  private def readSettings(config: Config) = {
    val cassandraConfig = config.getConfig("kamon.instrumentation.cassandra")

    Settings(
      sampleInterval                 = Duration.fromNanos(cassandraConfig.getDuration("metrics.sample-interval").toNanos),
      trackHostConnectionPoolMetrics = cassandraConfig.getBoolean("metrics.track-host-connection-pool-metrics"),
      hostTagMode                    = TagMode.from(cassandraConfig.getString("tracing.tags.host")),
      rackTagMode                    = TagMode.from(cassandraConfig.getString("tracing.tags.rack")),
      dcTagMode                      = TagMode.from(cassandraConfig.getString("tracing.tags.dc")),
      clusterTagMode                 = TagMode.from(cassandraConfig.getString("tracing.tags.cluster"))
    )
  }

  case class Node(address: String, dc: String, rack: String, cluster: String)

  case class Settings(
      sampleInterval:                 Duration,
      trackHostConnectionPoolMetrics: Boolean,
      hostTagMode:                    TagMode,
      rackTagMode:                    TagMode,
      dcTagMode:                      TagMode,
      clusterTagMode:                 TagMode
  )

  object Tags {
    val CassandraDriverComponent = "cassandra.driver"
    val Host                     = "cassandra.host"
    val DC                       = "cassandra.dc"
    val Rack                     = "cassandra.rack"
    val Cluster                  = "cassandra.cluster"
    val ErrorSource              = "source"
  }
}
