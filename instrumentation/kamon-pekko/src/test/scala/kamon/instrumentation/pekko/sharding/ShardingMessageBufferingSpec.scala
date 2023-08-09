package kamon.instrumentation.pekko.sharding

import org.apache.pekko.actor._
import org.apache.pekko.cluster.Cluster
import org.apache.pekko.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import org.apache.pekko.testkit.{ImplicitSender, TestKitBase}
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.pekko.ContextEchoActor
import kamon.testkit.{InitAndStopKamonAfterAll, MetricInspection}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class ShardingMessageBufferingSpec extends TestKitBase with AnyWordSpecLike with Matchers with ImplicitSender
    with MetricInspection.Syntax with InitAndStopKamonAfterAll {

  implicit lazy val system: ActorSystem = {
    ActorSystem("cluster-sharding-spec-system", ConfigFactory.parseString(
      """
        |pekko {
        |  loglevel = INFO
        |  loggers = [ "org.apache.pekko.event.slf4j.Slf4jLogger" ]
        |
        |  actor {
        |    provider = "cluster"
        |  }
        |
        |  remote.artery {
        |    canonical {
        |      hostname = "127.0.0.1"
        |      port = 2556
        |    }
        |  }
        |}
      """.stripMargin))
  }

  val remoteSystem: ActorSystem = ActorSystem("cluster-sharding-spec-remote-system", ConfigFactory.parseString(
    """
      |pekko {
      |  loglevel = INFO
      |  loggers = [ "org.apache.pekko.event.slf4j.Slf4jLogger" ]
      |
      |  actor {
      |    provider = "cluster"
      |  }
      |
      |  remote.artery {
      |    canonical {
      |      hostname = "127.0.0.1"
      |      port = 2557
      |    }
      |  }
      |}
    """.stripMargin))

  def contextWithBroadcast(name: String): Context =
    Context.Empty.withTag(
      ContextEchoActor.EchoTag, name
    )

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case entityId:String => (entityId, "reply-trace-token")
  }
  val extractShardId: ShardRegion.ExtractShardId = {
    case entityId:String => (entityId.toInt % 10).toString
  }

  "The MessageBuffer instrumentation" should {
    "propagate the current Context when sending message to a sharding region that has not been started" in {
      Cluster(system).join(Cluster(system).selfAddress)
      Cluster(remoteSystem).join(Cluster(system).selfAddress)

      val replierRegion: ActorRef = ClusterSharding(system).start(
        typeName = "replier",
        entityProps = ContextEchoActor.props(None),
        settings = ClusterShardingSettings(system),
        extractEntityId = extractEntityId,
        extractShardId = extractShardId)

      Kamon.runWithContext(contextWithBroadcast("cluster-sharding-actor-123")) {
        replierRegion ! "123"
      }

      expectMsg(10 seconds, "name=cluster-sharding-actor-123")
    }
  }
}
