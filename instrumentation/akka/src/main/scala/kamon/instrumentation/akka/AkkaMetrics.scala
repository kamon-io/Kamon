package kamon.instrumentation.akka

import kamon.Kamon
import kamon.instrumentation.akka.instrumentations.ActorCellInfo
import kamon.metric.InstrumentGroup
import kamon.tag.TagSet

import scala.collection.concurrent.TrieMap

object AkkaMetrics {

  private val _groupInstrumentsCache = TrieMap.empty[String, ActorGroupInstruments]
  private val _systemInstrumentsCache = TrieMap.empty[String, ActorSystemInstruments]

  /**
    * Actor Metrics
    */

  val ActorTimeInMailbox = Kamon.timer (
    name = "akka.actor.time-in-mailbox",
    description = "Tracks the time since the instant a message is enqueued in an Actor's mailbox until it is dequeued for processing"
  )

  val ActorProcessingTime = Kamon.timer (
    name = "akka.actor.processing-time",
    description = "Tracks the time taken for the actor to process the receive function"
  )

  val ActorMailboxSize = Kamon.rangeSampler(
    name = "akka.actor.mailbox-size",
    description = "Tracks the behavior of an Actor's mailbox size"
  )

  val ActorErrors = Kamon.counter (
    name = "akka.actor.errors",
    description = "Counts the number of processing errors experienced by an Actor"
  )

  def forActor(path: String, system: String, dispatcher: String, actorClass: Class[_]): ActorInstruments = {
    val tags = TagSet.builder()
      .add("path", path)
      .add("system", system)
      .add("dispatcher", dispatcher)
    if (!ActorCellInfo.isTyped(actorClass)) tags.add("class", actorClass.getName)
    new ActorInstruments(tags.build())
  }

  class ActorInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val timeInMailbox = register(ActorTimeInMailbox)
    val processingTime = register(ActorProcessingTime)
    val mailboxSize = register(ActorMailboxSize)
    val errors = register(ActorErrors)
  }


  /**
    * Router Metrics
    */

  val RouterRoutingTime = Kamon.timer (
    name = "akka.router.routing-time",
    description = "Tracks the time taken by a router to process its routing logic"
  )

  val RouterTimeInMailbox = Kamon.timer (
    name = "akka.router.time-in-mailbox",
    description = "Tracks the time since the instant a message is enqueued in a routee's mailbox until it is dequeued for processing"
  )

  val RouterProcessingTime = Kamon.timer (
    name = "akka.router.processing-time",
    description = "Tracks the time taken for a routee to process the receive function"
  )

  val RouterPendingMessages = Kamon.rangeSampler (
    name = "akka.router.pending-messages",
    description = "Tracks the number of messages waiting to be processed across all routees"
  )

  val RouterMembers = Kamon.rangeSampler (
    name = "akka.router.members",
    description = "Tracks the number of routees belonging to a router"
  )

  val RouterErrors = Kamon.counter (
    name = "akka.router.errors",
    description = "Counts the number of processing errors experienced by the routees of a router"
  )

  def forRouter(path: String, system: String, dispatcher: String, routerClass: Class[_], routeeClass: String): RouterInstruments = {
    val tags = TagSet.builder()
      .add("path", path)
      .add("system", system)
      .add("dispatcher", dispatcher)
      .add("routeeClass", routeeClass)
    if (!ActorCellInfo.isTyped(routerClass)) tags.add("routerClass", routerClass.getName)
    new RouterInstruments(tags.build())

  }

  class RouterInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val routingTime = register(RouterRoutingTime)
    val timeInMailbox = register(RouterTimeInMailbox)
    val processingTime = register(RouterProcessingTime)
    val pendingMessages = register(RouterPendingMessages)
    val members = register(RouterMembers)
    val errors = register(RouterErrors)
  }


  /**
    * Actor Group Metrics
    */

  val GroupTimeInMailbox = Kamon.timer (
    name = "akka.group.time-in-mailbox",
    description = "Tracks the time since the instant a message is enqueued in a member's mailbox until it is dequeued for processing"
  )

  val GroupProcessingTime = Kamon.timer (
    name = "akka.group.processing-time",
    description = "Tracks the time taken for a member actor to process the receive function"
  )

  val GroupPendingMessages = Kamon.rangeSampler (
    name = "akka.group.pending-messages",
    description = "Tracks the number of messages waiting to be processed across all members"
  )

  val GroupMembers = Kamon.rangeSampler (
    name = "akka.group.members",
    description = "Tracks the number of routees belonging to a group"
  )

  val GroupErrors = Kamon.counter (
    name = "akka.group.errors",
    description = "Counts the number of processing errors experienced by the members of a group"
  )

  def forGroup(group: String, system: String): ActorGroupInstruments =
    _groupInstrumentsCache.getOrElseUpdate(system + "/" + group, {
      val tags = TagSet.builder()
        .add("group", group)
        .add("system", system)

      new ActorGroupInstruments(tags.build())
    })


  case class ActorGroupInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val timeInMailbox = register(GroupTimeInMailbox)
    val processingTime = register(GroupProcessingTime)
    val pendingMessages = register(GroupPendingMessages)
    val members = register(GroupMembers)
    val errors = register(GroupErrors)
  }


  /**
    * Actor System Metrics
    */

  val SystemDeadLetters = Kamon.counter (
    name = "akka.system.dead-letters",
    description = "Counts the number of dead letters in an Actor System"
  )

  val SystemUnhandledMessages = Kamon.counter (
    name = "akka.system.unhandled-messages",
    description = "Counts the number of unhandled messages in an Actor System"
  )

  val SystemProcessedMessages = Kamon.counter (
    name = "akka.system.processed-messages",
    description = "Counts the number of processed messages in an Actor System"
  )

  val SystemActiveActors = Kamon.rangeSampler (
    name = "akka.system.active-actors",
    description = "Tracks the number of active Actors in an Actor System"
  )

  def forSystem(name: String): ActorSystemInstruments =
    _systemInstrumentsCache.getOrElseUpdate(name, new ActorSystemInstruments(TagSet.of("system", name)))

  class ActorSystemInstruments(tags: TagSet) extends InstrumentGroup(tags) {
    val deadLetters = register(SystemDeadLetters)
    val unhandledMessages = register(SystemUnhandledMessages)
    val processedMessagesByTracked = register(SystemProcessedMessages, "tracked", true)
    val processedMessagesByNonTracked = register(SystemProcessedMessages, "tracked", false)
    val activeActors = register(SystemActiveActors)
  }
}
