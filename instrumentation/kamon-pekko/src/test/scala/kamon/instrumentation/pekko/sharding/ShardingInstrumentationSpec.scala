package org.apache.pekko.kamon.instrumentation.pekko.sharding

import org.apache.pekko.actor._
import org.apache.pekko.cluster.sharding.ShardCoordinator.Internal.{HandOff, ShardStopped}
import org.apache.pekko.cluster.sharding.ShardCoordinator.ShardAllocationStrategy
import org.apache.pekko.cluster.sharding.ShardRegion.{GracefulShutdown, ShardId}
import org.apache.pekko.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import org.apache.pekko.testkit.TestActor.Watch
import org.apache.pekko.testkit.{ImplicitSender, TestKitBase}
import com.typesafe.config.ConfigFactory
import kamon.instrumentation.pekko.PekkoClusterShardingMetrics._
import kamon.tag.TagSet
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, MetricInspection}
import org.scalactic.TimesOnInt.convertIntToRepeater
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time._
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Random

case class TestMessage(shard: String, entity: String)

class ShardingInstrumentationSpec
    extends TestKitBase
    with AnyWordSpecLike with Matchers
    with ImplicitSender
    with MetricInspection.Syntax
    with InstrumentInspection.Syntax
    with InitAndStopKamonAfterAll
    with Eventually {

  lazy val system: ActorSystem = {
    ActorSystem(
      "sharding",
      ConfigFactory
        .parseString("""
        |pekko {
        |  loglevel = WARNING
        |  actor.provider = "cluster"
        |  remote.artery {
        |    canonical {
        |      hostname = "127.0.0.1"
        |      port = 2551
        |    }
        |  }
        |  cluster {
        |    seed-nodes = ["pekko://sharding@127.0.0.1:2551"]
        |    log-info = on
        |    cluster.jmx.multi-mbeans-in-same-jvm = on
        |  }
        |}
      """.stripMargin)
        .withFallback(ConfigFactory.load())
    )
  }

  val entityIdExtractor: ShardRegion.ExtractEntityId = { case msg @ TestMessage(_, entity) => (entity, msg) }
  val shardIdExtractor: ShardRegion.ExtractShardId = { case msg @ TestMessage(shard, _) => shard }

  val StaticAllocationStrategy = new ShardAllocationStrategy {
    override def allocateShard(
        requester: ActorRef,
        shardId: ShardId,
        currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]])
      : Future[ActorRef] = {
      Future.successful(requester)
    }

    override def rebalance(
        currentShardAllocations: Map[ActorRef, immutable.IndexedSeq[ShardId]],
        rebalanceInProgress: Set[ShardId]): Future[Set[ShardId]] = {
      Future.successful(Set.empty)
    }
  }

  def registerTypes(shardedType: String, props: Props, system: ActorSystem, allocationStrategy: ShardAllocationStrategy): ActorRef =
    ClusterSharding(system).start(
      typeName = shardedType,
      entityProps = props,
      settings = ClusterShardingSettings(system),
      extractEntityId = entityIdExtractor,
      extractShardId = shardIdExtractor,
      allocationStrategy = allocationStrategy,
      handOffStopMessage = PoisonPill
    )

  class ShardedTypeContext  {
    val shardType = s"TestType-${Random.nextLong()}"
    val region = registerTypes(shardType, TestActor.props(testActor), system, StaticAllocationStrategy)
    val shardTags = TagSet.builder()
      .add("type", shardType)
      .add("system", system.name)
      .build()
  }

  "the Cluster sharding instrumentation" should {
    "track shards, entities and messages" in new ShardedTypeContext {
      region ! TestMessage("s1", "e1")
      region ! TestMessage("s1", "e2")
      region ! TestMessage("s2", "e3")

      3 times {
        expectMsg("OK")
      }

      RegionProcessedMessages.withTags(shardTags).value() shouldBe 3L

      eventually(timeout(Span(2, Seconds))) {
        RegionHostedShards.withTags(shardTags).distribution().max shouldBe 2L
        RegionHostedEntities.withTags(shardTags).distribution().max shouldBe 3L
      }

      eventually(timeout(Span(2, Seconds))) {
        ShardProcessedMessages.withTags(shardTags).distribution(resetState = false).sum shouldBe 3L
        ShardHostedEntities.withTags(shardTags).distribution(resetState = false).max shouldBe 2L
      }
    }

    "clean metrics on handoff" in new ShardedTypeContext {
      region ! TestMessage("s1", "e1")
      expectMsg("OK")

      eventually(timeout(Span(2, Seconds))) {
        RegionHostedShards.withTags(shardTags).distribution().max shouldBe 1L
        RegionHostedEntities.withTags(shardTags).distribution().max shouldBe 1L
      }

      region ! HandOff("s1")
      expectMsg(ShardStopped("s1"))

      eventually(timeout(Span(10, Seconds))) {
        RegionHostedShards.withTags(shardTags).distribution().max shouldBe 0L
        RegionHostedEntities.withTags(shardTags).distribution().max shouldBe 0L
      }
    }

    "clean metrics on shutdown" in new ShardedTypeContext {
      region ! TestMessage("s1", "e1")
      expectMsg("OK")

      RegionHostedShards.tagValues("type") should contain(shardType)
      RegionHostedEntities.tagValues("type") should contain(shardType)
      RegionProcessedMessages.tagValues("type") should contain(shardType)

      testActor ! Watch(region)
      region ! GracefulShutdown
      expectTerminated(region)

      RegionHostedShards.tagValues("type") should not contain(shardType)
      RegionHostedEntities.tagValues("type") should not contain(shardType)
      RegionProcessedMessages.tagValues("type") should not contain(shardType)
    }
  }

}

object TestActor {

  def props(testActor: ActorRef) =
    Props(classOf[TestActor], testActor)
}

class TestActor(testActor: ActorRef) extends Actor {

  override def receive: Actor.Receive = {
    case _ => testActor ! "OK"
  }
}
