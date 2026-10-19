package kamon.instrumentation
package pekko.instrumentations

import org.apache.pekko.actor.{Actor, Address, ExtendedActorSystem, Props}
import org.apache.pekko.cluster.{Cluster, ClusterEvent, MemberStatus}
import kamon.Kamon
import kamon.instrumentation.pekko.PekkoInstrumentation
import kamon.metric.Gauge
import kamon.tag.TagSet
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

import scala.annotation.static
import scala.collection.mutable

class ClusterInstrumentation extends InstrumentationBuilder {

  onType("org.apache.pekko.cluster.Cluster$")
    .advise(method("createExtension").and(takesArguments(1)), classOf[AfterClusterInitializationAdvice])
}

class AfterClusterInitializationAdvice
object AfterClusterInitializationAdvice {

  @Advice.OnMethodExit
  @static def onClusterExtensionCreated(
    @Advice.Argument(0) system: ExtendedActorSystem,
    @Advice.Return clusterExtension: Cluster
  ): Unit = {
    val settings = PekkoInstrumentation.settings()
    if (settings.exposeClusterMetrics) {
      val stateExporter =
        system.systemActorOf(Props[ClusterInstrumentation.ClusterStateExporter](), "kamon-cluster-state-exporter")
      clusterExtension.subscribe(stateExporter, classOf[ClusterEvent.ClusterDomainEvent])
    }
  }
}

object ClusterInstrumentation {

  class ClusterStateExporter extends Actor {
    private val clusterExtension = Cluster(context.system)
    private val clusterTags = TagSet.of("pekko.system.name", context.system.name)

    private val joiningMembers = ClusterMembersJoining.withTags(clusterTags)
    private val weaklyUpMembers = ClusterMembersWeaklyUp.withTags(clusterTags)
    private val upMembers = ClusterMembersUp.withTags(clusterTags)
    private val leavingMembers = ClusterMembersLeaving.withTags(clusterTags)
    private val exitingMembers = ClusterMembersExiting.withTags(clusterTags)
    private val downMembers = ClusterMembersDown.withTags(clusterTags)
    private val removedMembers = ClusterMembersRemoved.withTags(clusterTags)
    private val totalMembers = ClusterMembersTotal.withTags(clusterTags)
    private val unreachableMembers = ClusterMembersUnreachable.withTags(clusterTags)
    private val unreachableDatacenters = ClusterDatacentersUnreachable.withTags(clusterTags)
    private val monitoredNodes = mutable.HashMap.empty[Address, (Gauge, Gauge)]

    override def receive: Receive = {
      case _: ClusterEvent.ClusterDomainEvent             => updateAllStates(clusterExtension.state)
      case initialState: ClusterEvent.CurrentClusterState => updateAllStates(initialState)
    }

    private def updateAllStates(clusterState: ClusterEvent.CurrentClusterState): Unit = {
      val membersPerStatus = clusterState.members.groupBy(_.status)
      joiningMembers.update(membersPerStatus.getOrElse(MemberStatus.Joining, Set.empty).size)
      weaklyUpMembers.update(membersPerStatus.getOrElse(MemberStatus.WeaklyUp, Set.empty).size)
      upMembers.update(membersPerStatus.getOrElse(MemberStatus.Up, Set.empty).size)
      leavingMembers.update(membersPerStatus.getOrElse(MemberStatus.Leaving, Set.empty).size)
      exitingMembers.update(membersPerStatus.getOrElse(MemberStatus.Exiting, Set.empty).size)
      downMembers.update(membersPerStatus.getOrElse(MemberStatus.Down, Set.empty).size)

      val removedMembersCount = membersPerStatus.getOrElse(MemberStatus.Removed, Set.empty).size
      val totalMembersCount = clusterState.members.size - removedMembersCount
      removedMembers.update(removedMembersCount)
      totalMembers.update(totalMembersCount)

      unreachableMembers.update(clusterState.unreachable.size)
      unreachableDatacenters.update(clusterState.unreachableDataCenters.size)

      // The status and reachability gauges will only be published for the subset of members that are currently being
      // monitored by this node.
      val currentlyMonitoredMembers =
        clusterState.members.filter(m => clusterExtension.failureDetector.isMonitoring(m.address))
      val currentlyMonitoredAddresses = currentlyMonitoredMembers.map { member =>
        val (statusGauge, reachabilityGauge) = monitoredNodes.getOrElseUpdate(
          member.address, {
            val memberTags = clusterTags.withTag("member", member.address.toString)

            (
              ClusterMemberStatus.withTags(memberTags),
              ClusterMemberReachability.withTags(memberTags)
            )
          }
        )

        statusGauge.update(statusToGaugeValue(member.status))
        reachabilityGauge.update(if (clusterState.unreachable(member)) 1d else 0d)
        member.address
      }

      // Remove any cached Gauges for members that we might not be monitoring anymore
      monitoredNodes.keys.filterNot(a => currentlyMonitoredAddresses(a)).foreach { addressToRemove =>
        monitoredNodes.remove(addressToRemove).foreach {
          case (statusGauge, reachabilityGauge) =>
            statusGauge.remove()
            reachabilityGauge.remove()
        }
      }
    }

    private def statusToGaugeValue(memberStatus: MemberStatus): Double = memberStatus match {
      case MemberStatus.Joining  => 1
      case MemberStatus.WeaklyUp => 2
      case MemberStatus.Up       => 3
      case MemberStatus.Leaving  => 4
      case MemberStatus.Exiting  => 5
      case MemberStatus.Down     => 6
      case MemberStatus.Removed  => 7
      case _                     => 0 // This should never happen, but covering the bases here
    }
  }

  val ClusterMembersJoining = Kamon.gauge(
    name = "pekko.cluster.members.joining.count",
    description = "Tracks the number of cluster members in the Joining state"
  )

  val ClusterMembersWeaklyUp = Kamon.gauge(
    name = "pekko.cluster.members.weakly-up.count",
    description = "Tracks the number of cluster members in the Weakly-Up state"
  )

  val ClusterMembersUp = Kamon.gauge(
    name = "pekko.cluster.members.up.count",
    description = "Tracks the number of cluster members in the Up state"
  )

  val ClusterMembersLeaving = Kamon.gauge(
    name = "pekko.cluster.members.leaving.count",
    description = "Tracks the number of cluster members in the Leaving state"
  )

  val ClusterMembersExiting = Kamon.gauge(
    name = "pekko.cluster.members.exiting.count",
    description = "Tracks the number of cluster members in the Exiting state"
  )

  val ClusterMembersDown = Kamon.gauge(
    name = "pekko.cluster.members.down.count",
    description = "Tracks the number of cluster members in the Down state"
  )

  val ClusterMembersRemoved = Kamon.gauge(
    name = "pekko.cluster.members.removed.count",
    description = "Tracks the number of cluster members in the Removed state"
  )

  val ClusterMembersTotal = Kamon.gauge(
    name = "pekko.cluster.members.total.count",
    description = "Tracks the total number of cluster members, without including Removed members"
  )

  val ClusterMembersUnreachable = Kamon.gauge(
    name = "pekko.cluster.members.unreachable.count",
    description = "Tracks the total number of cluster members marked as unreachable"
  )

  val ClusterDatacentersUnreachable = Kamon.gauge(
    name = "pekko.cluster.datacenters.unreachable.count",
    description = "Tracks the total number of cluster members marked as unreachable"
  )

  val ClusterMemberStatus = Kamon.gauge(
    name = "pekko.cluster.members.status",
    description = "Tracks the current status of all monitored nodes by a cluster member"
  )

  val ClusterMemberReachability = Kamon.gauge(
    name = "pekko.cluster.members.reachability",
    description = "Tracks the current reachability status of all monitored nodes by a cluster member"
  )
}
