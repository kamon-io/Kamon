package kamon.akka.sharding.metrics

import akka.cluster.sharding.ShardRegion.ShardState
import kamon.metric.instrument.InstrumentFactory
import kamon.metric.{ EntityRecorderFactory, GenericEntityRecorder }

class AkkaShardMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {

  val numShardsGauge = gauge("number-of-shards", 0L)

  def record(shardStats: Set[ShardState]) = {
    numShardsGauge.record(shardStats.size)
    shardStats.foreach(state ⇒ {
      val shardGauge = gauge(s"number-of-entities-shard-${state.shardId}", 0L)
      shardGauge.record(state.entityIds.size)
    })
  }
}

object AkkaShardMetrics extends EntityRecorderFactory[AkkaShardMetrics] {
  val ShardingCategoryName = "akka-sharding"
  def category: String = ShardingCategoryName
  override def createRecorder(instrumentFactory: InstrumentFactory): AkkaShardMetrics = new AkkaShardMetrics(instrumentFactory)
}