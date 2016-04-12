package akka.cluster.sharding

import akka.cluster.sharding.ShardCoordinator.Internal.HandOff

trait HandOffHelper {
  def handOff(shardId: String) = HandOff(shardId)
}
