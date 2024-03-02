package kamon.instrumentation.pekko

import com.typesafe.config.Config

import java.util.concurrent.{ScheduledFuture, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import kamon.{AtomicGetOrElseUpdateOnTrieMap, Kamon}
import kamon.metric.{Histogram, InstrumentGroup}
import kamon.module.Module.Registration
import kamon.module.ScheduledAction
import kamon.tag.TagSet

import scala.collection.concurrent.TrieMap

object PekkoClusterShardingMetrics {

  val RegionHostedShards = Kamon.rangeSampler(
    name = "pekko.cluster.sharding.region.hosted-shards",
    description = "Tracks the number of shards hosted by a region"
  )

  val RegionHostedEntities = Kamon.rangeSampler(
    name = "pekko.cluster.sharding.region.hosted-entities",
    description = "Tracks the number of entities hosted by a region"
  )

  val RegionProcessedMessages = Kamon.counter(
    name = "pekko.cluster.sharding.region.processed-messages",
    description = "Counts the number of messages processed by a region"
  )

  val ShardHostedEntities = Kamon.histogram(
    name = "pekko.cluster.sharding.shard.hosted-entities",
    description = "Tracks the distribution of entity counts hosted per shard"
  )

  val ShardProcessedMessages = Kamon.histogram(
    name = "pekko.cluster.sharding.shard.processed-messages",
    description = "Tracks the distribution of processed messages per shard"
  )

  class ShardingInstruments(system: String, typeName: String)
      extends InstrumentGroup(TagSet.of("type", typeName).withTag("system", system)) {

    val hostedShards = register(RegionHostedShards)
    val hostedEntities = register(RegionHostedEntities)
    val processedMessages = register(RegionProcessedMessages)
    val shardHostedEntities = register(ShardHostedEntities)
    val shardProcessedMessages = register(ShardProcessedMessages)

    private val _shardTelemetry =
      ShardingInstruments.shardTelemetry(system, typeName, shardHostedEntities, shardProcessedMessages)

    def hostedEntitiesPerShardCounter(shardID: String): AtomicLong =
      _shardTelemetry.entitiesPerShard.getOrElseUpdate(shardID, new AtomicLong())

    def processedMessagesPerShardCounter(shardID: String): AtomicLong =
      _shardTelemetry.messagesPerShard.getOrElseUpdate(shardID, new AtomicLong())

    // We should only remove when the ShardRegion actor is terminated.
    override def remove(): Unit = {
      ShardingInstruments.removeShardTelemetry(system, typeName)
      super.remove()
    }
  }

  object ShardingInstruments {

    /**
      * Assist with tracking the number of entities hosted by a Shard and the number of messages processed by each
      * Shard. Note that there is a difference in the hosted entities and processed messages at the Region level versus
      * at the Shard Level: there is only one Region per type per node, so the number of processed messages is a clear
      * indication of how many messages were processed and how many entities are hosted in the region; there can (and
      * will be) many Shards on the same node which could generate cardinality issues if we were tracking metrics for
      * each individual Shard so, instead, we track the distribution of entities and processed messages across all
      * Shards. This behavior can help uncover cases in which Shards are not evenly distributed (both in the messages
      * volume and hosted entities aspects) but cannot point out which of the Shards deviates from the common case.
      *
      * The totals per Shard are tracked locally and sampled in a fixed interval.
      */
    case class ShardTelemetry(
      entitiesPerShard: TrieMap[String, AtomicLong],
      messagesPerShard: TrieMap[String, AtomicLong],
      schedule: Registration
    )

    private val _shardTelemetryMap = TrieMap.empty[String, ShardTelemetry]

    private def shardTelemetry(
      system: String,
      typeName: String,
      shardEntities: Histogram,
      shardMessages: Histogram
    ): ShardTelemetry = {
      _shardTelemetryMap.atomicGetOrElseUpdate(
        shardTelemetryKey(system, typeName), {
          val entitiesPerShard = TrieMap.empty[String, AtomicLong]
          val messagesPerShard = TrieMap.empty[String, AtomicLong]
          val samplingInterval = PekkoRemoteInstrumentation.settings().shardMetricsSampleInterval

          val schedule = Kamon.addScheduledAction(
            s"pekko/shards/${typeName}",
            Some(
              s"Updates health metrics for the ${system}/${typeName} shard every ${samplingInterval.getSeconds} seconds"
            ),
            new ScheduledAction {
              override def run(): Unit = {
                entitiesPerShard.foreach { case (shard, value) => shardEntities.record(value.get()) }
                messagesPerShard.foreach { case (shard, value) => shardMessages.record(value.getAndSet(0L)) }
              }

              override def stop(): Unit = {}
              override def reconfigure(newConfig: Config): Unit = {}

            },
            samplingInterval
          )

          ShardTelemetry(entitiesPerShard, messagesPerShard, schedule)
        },
        _.schedule.cancel(): Unit,
        _ => ()
      )
    }

    private def removeShardTelemetry(system: String, typeName: String): Unit =
      _shardTelemetryMap.remove(shardTelemetryKey(system, typeName)).foreach(_.schedule.cancel())

    private def shardTelemetryKey(system: String, typeName: String): String =
      system + ":" + typeName
  }
}
