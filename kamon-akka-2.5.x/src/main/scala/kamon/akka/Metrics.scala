/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */
package kamon.akka

import kamon.Kamon
import kamon.metric.MeasurementUnit._
import kamon.metric._
import kamon.tag.TagSet



object Metrics {

  /**
    *
    *  Metrics for Akka Actors:
    *
    *    - time-in-mailbox: Time spent from the instant when a message is enqueued in an actor's mailbox to the instant when
    *      that message is dequeued for processing.
    *    - processing-time: Time taken for the actor to process the receive function.
    *    - mailbox-size: Size of the actor's mailbox.
    *    - errors: Number of errors seen by the actor's supervision mechanism.
    */
  val actorTimeInMailboxMetric = Kamon.histogram("akka.actor.time-in-mailbox", time.nanoseconds)
  val actorProcessingTimeMetric = Kamon.histogram("akka.actor.processing-time", time.nanoseconds)
  val actorMailboxSizeMetric = Kamon.rangeSampler("akka.actor.mailbox-size")
  val actorErrorsMetric = Kamon.counter("akka.actor.errors")

  def forActor(path: String, system: String, dispatcher: String, actorClass: String): ActorMetrics = {
    val actorTags = TagSet.from(Map("path" -> path, "system" -> system, "dispatcher" -> dispatcher, "class" -> actorClass))
    ActorMetrics(
      actorTimeInMailboxMetric.withTags(actorTags),
      actorProcessingTimeMetric.withTags(actorTags),
      actorMailboxSizeMetric.withTags(actorTags),
      actorErrorsMetric.withTags(actorTags)
    )
  }

  case class ActorMetrics(timeInMailbox: Histogram, processingTime: Histogram,
    mailboxSize: RangeSampler, errors: Counter) {
    def cleanup(): Unit = {
      timeInMailbox.remove()
      processingTime.remove()
      mailboxSize.remove()
      errors.remove()
    }
  }


  /**
    *
    *  Metrics for Akka Routers.
    *
    *    - routing-time: Time taken for the router to process the routing logic.
    *    - time-in-mailbox: Time spent from the instant when a message is enqueued in an actor's mailbox to the instant when
    *      that message is dequeued for processing.
    *    - processing-time: Time taken for the actor to process the receive function.
    *    - errors: Number or errors seen by the actor's supervision mechanism.
    *    - pending-messages: Number of messages waiting to be processed across all routees.
    */
  val routerRoutingTime = Kamon.histogram("akka.router.routing-time", time.nanoseconds)
  val routerTimeInMailbox = Kamon.histogram("akka.router.time-in-mailbox", time.nanoseconds)
  val routerProcessingTime = Kamon.histogram("akka.router.processing-time", time.nanoseconds)
  val routerPendingMessages = Kamon.rangeSampler("akka.router.pending-messages")
  val routerMembers = Kamon.rangeSampler("akka.router.members")
  val routerErrors = Kamon.counter("akka.router.errors")

  def forRouter(path: String, system: String, dispatcher: String, routerClass: String, routeeClass: String): RouterMetrics = {
    val routerTags = TagSet.from(
      Map(
        "path" -> path,
        "system" -> system,
        "dispatcher" -> dispatcher,
        "routerClass" -> routerClass,
        "routeeClass" -> routeeClass
      )
    )

    RouterMetrics(
      routerRoutingTime.withTags(routerTags),
      routerTimeInMailbox.withTags(routerTags),
      routerProcessingTime.withTags(routerTags),
      routerPendingMessages.withTags(routerTags),
      routerMembers.withTags(routerTags),
      routerErrors.withTags(routerTags)
    )
  }

  case class RouterMetrics(routingTime: Histogram, timeInMailbox: Histogram,
      processingTime: Histogram, pendingMessages: RangeSampler, members: RangeSampler, errors: Counter) {

    def cleanup(): Unit = {
      routingTime.remove()
      timeInMailbox.remove()
      processingTime.remove()
      pendingMessages.remove()
      members.remove()
      errors.remove()
    }
  }


  /**
    *
    *  Metrics for Groups. Sums across all actors in the Actor Group.
    *  The metrics being tracked are:
    *
    *    - time-in-mailbox: Time spent from the instant when a message is enqueued in an actor's mailbox to the instant when
    *      that message is dequeued for processing.
    *    - processing-time: Time taken for the actor to process the receive function.
    *    - members: Number of group members that have been created.
    *    - mailbox-size: Size of the actor's mailbox.
    *    - errors: Number of errors seen by the actor's supervision mechanism.
    */
  val groupTimeInMailbox = Kamon.histogram("akka.group.time-in-mailbox", time.nanoseconds)
  val groupProcessingTime = Kamon.histogram("akka.group.processing-time", time.nanoseconds)
  val groupPendingMessages = Kamon.rangeSampler("akka.group.pending-messages")
  val groupMembers = Kamon.rangeSampler("akka.group.members")
  val groupErrors = Kamon.counter("akka.group.errors")

  def forGroup(group: String, system: String): ActorGroupMetrics = {
    val actorTags = TagSet.from(
      Map("group" -> group, "system" -> system)
    )
    ActorGroupMetrics(
      groupTimeInMailbox.withTags(actorTags),
      groupProcessingTime.withTags(actorTags),
      groupPendingMessages.withTags(actorTags),
      groupMembers.withTags(actorTags),
      groupErrors.withTags(actorTags)
    )
  }

  case class ActorGroupMetrics(timeInMailbox: Histogram, processingTime: Histogram,
      pendingMessages: RangeSampler, members: RangeSampler, errors: Counter) {

    def cleanup(): Unit = {
      timeInMailbox.remove()
      processingTime.remove()
      pendingMessages.remove()
      members.remove()
      errors.remove()
    }
  }



  /**
    *
    *  Metrics for actor systems.
    *
    *    - dead-letters: System global counter for messages received by dead letters.
    *    - processed-messages: System global count of processed messages (separate for tracked and non-tracked)
    *    - active-actors: Current count of active actors in the system.
    */
  val systemDeadLetters = Kamon.counter("akka.system.dead-letters")
  val systemUnhandledMessages = Kamon.counter("akka.system.unhandled-messages")
  val systemProcessedMessages = Kamon.counter("akka.system.processed-messages")
  val systemActiveActors = Kamon.rangeSampler("akka.system.active-actors")

  def forSystem(name: String): ActorSystemMetrics = {
    val systemTags = TagSet.of("system", name)

    ActorSystemMetrics(
      systemDeadLetters.withTags(systemTags),
      systemUnhandledMessages.withTags(systemTags),
      systemProcessedMessages.withTags(systemTags.withTag("tracked", "true")),
      systemProcessedMessages.withTags(systemTags.withTag("tracked", "false")),
      systemActiveActors.withTags(systemTags)
    )
  }

  case class ActorSystemMetrics(deadLetters: Counter, unhandledMessages: Counter,
      processedMessagesByTracked: Counter, processedMessagesByNonTracked: Counter, activeActors: RangeSampler) {

    def cleanup(): Unit = {
      deadLetters.remove()
      unhandledMessages.remove()
      processedMessagesByTracked.remove()
      processedMessagesByNonTracked.remove()
      activeActors.remove()
    }
  }
}