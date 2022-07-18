package kamon.instrumentation
package akka.instrumentations

import _root_.akka.actor.{Actor, ExtendedActorSystem, Props}
import _root_.akka.cluster.{Cluster, ClusterEvent, MemberStatus}
import kamon.Kamon
import kanela.agent.api.instrumentation.InstrumentationBuilder
import kanela.agent.libs.net.bytebuddy.asm.Advice

class ClusterInstrumentation extends InstrumentationBuilder with VersionFiltering {

  onAkka("2.5", "2.6") {
    onType("akka.cluster.Cluster$")
      .advise(method("createExtension").and(takesArguments(1)), AfterClusterInitializationAdvice)
  }
}

object AfterClusterInitializationAdvice {

  @Advice.OnMethodExit
  def onClusterExtensionCreated(@Advice.Argument(0) system: ExtendedActorSystem, @Advice.Return clusterExtension: Cluster): Unit = {
    val stateExporter = system.systemActorOf(Props[ClusterInstrumentation.ClusterStateExporter], "kamon-cluster-state-exporter")
    clusterExtension.subscribe(stateExporter, ClusterEvent.InitialStateAsSnapshot, classOf[ClusterEvent.ClusterDomainEvent])
  }
}

object ClusterInstrumentation {

  class ClusterStateExporter extends Actor {
    private val clusterExtension = Cluster(context.system)
    private val joiningMembers = ClusterMembersJoining.withTag("akka.system.name", context.system.name)
    private val weaklyUpMembers = ClusterMembersWeaklyUp.withTag("akka.system.name", context.system.name)
    private val upMembers = ClusterMembersUp.withTag("akka.system.name", context.system.name)
    private val leavingMembers = ClusterMembersLeaving.withTag("akka.system.name", context.system.name)
    private val exitingMembers = ClusterMembersExiting.withTag("akka.system.name", context.system.name)
    private val downMembers = ClusterMembersDown.withTag("akka.system.name", context.system.name)
    private val removedMembers = ClusterMembersRemoved.withTag("akka.system.name", context.system.name)
    private val totalMembers = ClusterMembersTotal.withTag("akka.system.name", context.system.name)
    private val unreachableMembers = ClusterMembersUnreachable.withTag("akka.system.name", context.system.name)
    private val unreachableDatacenters = ClusterDatacentersUnreachable.withTag("akka.system.name", context.system.name)

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
    }
  }

  val ClusterMembersJoining = Kamon.gauge(
    name = "akka.cluster.members.joining.count",
    description = "Tracks the number of cluster members in the Joining state"
  )

  val ClusterMembersWeaklyUp = Kamon.gauge(
    name = "akka.cluster.members.weakly-up.count",
    description = "Tracks the number of cluster members in the Weakly-Up state"
  )

  val ClusterMembersUp = Kamon.gauge(
    name = "akka.cluster.members.up.count",
    description = "Tracks the number of cluster members in the Up state"
  )

  val ClusterMembersLeaving = Kamon.gauge(
    name = "akka.cluster.members.leaving.count",
    description = "Tracks the number of cluster members in the Leaving state"
  )

  val ClusterMembersExiting = Kamon.gauge(
    name = "akka.cluster.members.exiting.count",
    description = "Tracks the number of cluster members in the Exiting state"
  )

  val ClusterMembersDown = Kamon.gauge(
    name = "akka.cluster.members.down.count",
    description = "Tracks the number of cluster members in the Down state"
  )

  val ClusterMembersRemoved = Kamon.gauge(
    name = "akka.cluster.members.removed.count",
    description = "Tracks the number of cluster members in the Removed state"
  )

  val ClusterMembersTotal = Kamon.gauge(
    name = "akka.cluster.members.total.count",
    description = "Tracks the total number of cluster members, without including Removed members"
  )

  val ClusterMembersUnreachable = Kamon.gauge(
    name = "akka.cluster.members.unreachable.count",
    description = "Tracks the total number of cluster members marked as unreachable"
  )

  val ClusterDatacentersUnreachable = Kamon.gauge(
    name = "akka.cluster.datacenters.unreachable.count",
    description = "Tracks the total number of cluster members marked as unreachable"
  )
}
