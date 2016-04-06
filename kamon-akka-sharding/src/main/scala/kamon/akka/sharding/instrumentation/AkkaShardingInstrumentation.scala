package akka.kamon.instrumentation

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ Executors, ThreadFactory, TimeUnit }

import akka.actor.{ ActorRef, ExtendedActorSystem, Props }
import akka.cluster.sharding.ShardCoordinator.ShardAllocationStrategy
import akka.cluster.sharding.ShardRegion.{ CurrentShardRegionState, ExtractEntityId, ExtractShardId, GetShardRegionState }
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings }
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import kamon.Kamon
import kamon.akka.sharding.metrics.AkkaShardMetrics
import kamon.metric.Entity
import org.aspectj.lang.annotation._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object AkkaShardingInstrumentation {
  implicit lazy val ec: ExecutionContext = {
    val threadFactory = new ThreadFactory {

      val counter: AtomicInteger = new AtomicInteger()
      val group: ThreadGroup = new ThreadGroup("kamon-akka-sharding")

      override def newThread(r: Runnable): Thread =
        new Thread(group, r, s"metrics-refresher-${counter.getAndIncrement()}")
    }
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5, threadFactory))
  }
  implicit val sender: ActorRef = ActorRef.noSender
  implicit val timeout: Timeout = 10 seconds

  val config: Config = Kamon.config.getConfig("kamon.akka-sharding")

  implicit def toFiniteDuration(d: java.time.Duration): FiniteDuration = FiniteDuration(d.toNanos, TimeUnit.NANOSECONDS)

}

@Aspect
class AkkaShardingInstrumentation {

  @Pointcut("execution(* akka.cluster.sharding.ClusterSharding.start(..)) && this(clusterSharding) && args(typeName, entityProps, settings, extractEntityId, extractShardId, allocationStrategy, handOffStopMessage)")
  def constructShardRegion(clusterSharding: ClusterSharding, typeName: String, entityProps: Props, settings: ClusterShardingSettings, extractEntityId: ExtractEntityId, extractShardId: ExtractShardId, allocationStrategy: ShardAllocationStrategy, handOffStopMessage: Any): Unit = {}

  @AfterReturning(pointcut = "constructShardRegion(clusterSharding, typeName, entityProps, settings, extractEntityId, extractShardId, allocationStrategy, handOffStopMessage)", returning = "shardRegion")
  def afterShardRegionConstructed(clusterSharding: ClusterSharding, typeName: String, entityProps: Props, settings: ClusterShardingSettings, extractEntityId: ExtractEntityId, extractShardId: ExtractShardId, allocationStrategy: ShardAllocationStrategy, handOffStopMessage: Any, shardRegion: ActorRef): Unit = {
    val trackedSystem = clusterSharding.asInstanceOf[ActorSystemAware].actorSystem
    val entity = Entity(s"${trackedSystem.name}/$typeName", AkkaShardMetrics.ShardingCategoryName)
    if (Kamon.metrics.shouldTrack(entity)) {
      val shardEntity = Kamon.metrics.entity(AkkaShardMetrics, entity)
      import AkkaShardingInstrumentation._
      val scheduler = Kamon.metrics.settings.refreshScheduler
      val interval: FiniteDuration = config.getDuration("refresh-interval")
      scheduler.schedule(interval, () ⇒ {
        val shardStateFuture = shardRegion ? GetShardRegionState
        shardStateFuture.mapTo[CurrentShardRegionState].foreach(state ⇒ {
          shardEntity.record(state.shards)
        })
      })
    }
  }

  @Pointcut("initialization(akka.cluster.sharding.ClusterSharding.new(..)) && target(clusterSharding) && args(system)")
  def clusterShardingCreation(clusterSharding: ClusterSharding, system: ExtendedActorSystem): Unit = {}

  @After("clusterShardingCreation(clusterSharding, system)")
  def afterClusterShardingCreation(clusterSharding: ClusterSharding, system: ExtendedActorSystem): Unit = {
    clusterSharding.asInstanceOf[ActorSystemAware].actorSystem = system
  }

  @DeclareMixin("akka.cluster.sharding.ClusterSharding")
  def clusterShardingActorSystem: ActorSystemAware = ActorSystemAware()

}
