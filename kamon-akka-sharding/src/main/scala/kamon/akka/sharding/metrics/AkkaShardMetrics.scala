package kamon.akka.sharding.metrics

import akka.cluster.sharding.ShardRegion.ShardState
import kamon.metric.instrument.Gauge.CurrentValueCollector
import kamon.metric.instrument.InstrumentFactory
import kamon.metric.{ EntityRecorderFactory, GenericEntityRecorder }

import scala.collection.mutable

class AkkaShardMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {

  private val shardStateValueCollectors = mutable.Map[String, UpdateableCurrentValueCollector]()
  private val numberOfShardsValueCollector = new UpdateableCurrentValueCollector(0L)
  val numShardsGauge = gauge("number-of-shards", numberOfShardsValueCollector)

  def record(shardStats: Set[ShardState]) = {
    numberOfShardsValueCollector.currentValue = shardStats.size

    val prevShards = shardStateValueCollectors.keys
    val currentShards = shardStats.map(shardId ⇒ s"number-of-entities-shard-$shardId")

    prevShards.filterNot(currentShards.contains).foreach(removedShard ⇒ {
      shardStateValueCollectors.remove(removedShard)
      removeGauge(removedShard)
    })

    shardStats.foreach(state ⇒ {
      val key = s"number-of-entities-shard-${state.shardId}"
      val valueCollector = shardStateValueCollectors.getOrElseUpdate(key, new UpdateableCurrentValueCollector(0L))
      gauge(key, valueCollector)
      valueCollector.currentValue = state.entityIds.size
    })
  }
}

object AkkaShardMetrics extends EntityRecorderFactory[AkkaShardMetrics] {
  val ShardingCategoryName = "akka-sharding"
  def category: String = ShardingCategoryName
  override def createRecorder(instrumentFactory: InstrumentFactory): AkkaShardMetrics = new AkkaShardMetrics(instrumentFactory)
}

class UpdateableCurrentValueCollector(var currentValue: Long) extends CurrentValueCollector