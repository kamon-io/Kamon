package kamon.akka.sharding

import akka.actor.{ Actor, ActorSystem, Props }
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings, ShardRegion }
import akka.testkit.{ ImplicitSender, TestKitBase }
import kamon.Kamon
import kamon.akka.sharding.ShardedActor.{ Ping, Pong }
import kamon.akka.sharding.metrics.AkkaShardMetrics$
import kamon.metric.{ Entity, EntitySnapshot }
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatest.{ Matchers, WordSpecLike }

class AkkaShardingMetricsSpec extends TestKitBase with WordSpecLike with Matchers with ImplicitSender {

  lazy val collectionContext = Kamon.metrics.buildDefaultCollectionContext

  implicit lazy val system = {
    Kamon.start()
    ActorSystem("sharding-system")
  }

  implicit val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(500, Millis)))

  "the Kamon akka sharding metrics" should {

    "track included sharding entity" in {
      val shardRegion = ClusterSharding(system).start("tracked-shard", Props(new ShardedActor), ClusterShardingSettings(system), ShardedActor.extractEntityId, ShardedActor.extractShardId)
      shardRegion ! Ping(1)
      expectMsg(Pong())
      val trackedEntity = Kamon.metrics.find("sharding-system/tracked-shard", "akka-sharding")
      trackedEntity should not be empty
    }

    "not track excluded sharding entity" in {
      val shardRegion = ClusterSharding(system).start("untracked-shard", Props(new ShardedActor), ClusterShardingSettings(system), ShardedActor.extractEntityId, ShardedActor.extractShardId)
      shardRegion ! Ping(1)
      expectMsg(Pong())
      val untrackedEntity = Kamon.metrics.find("sharding-system/untracked-shard", "akka-sharding")
      untrackedEntity shouldBe empty
    }

    "report correct metrics" in {
      val shardRegion = ClusterSharding(system).start("tracked-shard", Props(new ShardedActor), ClusterShardingSettings(system), ShardedActor.extractEntityId, ShardedActor.extractShardId)
      shardRegion ! Ping(1)
      expectMsg(Pong())
      shardRegion ! Ping(2)
      expectMsg(Pong())
      eventually {
        val entity = Kamon.metrics.find(Entity("sharding-system/tracked-shard", "akka-sharding"))
        val snapshot = entity.get.collect(collectionContext)
        val numShards = snapshot.gauge("number-of-shards")
        val entitiesShard1 = snapshot.gauge("number-of-entities-shard-1")
        val entitiesShard2 = snapshot.gauge("number-of-entities-shard-2")
        numShards.get.max shouldBe 2
        entitiesShard1.get.max shouldBe 1
        entitiesShard2.get.max shouldBe 1
      }
    }
  }
}

object ShardedActor {

  case class Ping(entityId: Int)

  case class Pong()

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ Ping(entityId) ⇒ (entityId.toString, msg)
  }

  val numberOfShards = 5

  val extractShardId: ShardRegion.ExtractShardId = {
    case Ping(entityId) ⇒ (entityId % numberOfShards).toString
  }
}

class ShardedActor extends Actor {
  override def receive: Receive = {
    case Ping(entityId) ⇒ sender ! Pong()
  }
}
