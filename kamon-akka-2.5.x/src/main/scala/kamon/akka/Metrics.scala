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
    val actorTags = Map("path" -> path, "system" -> system, "dispatcher" -> dispatcher, "class" -> actorClass)
    ActorMetrics(
    actorTags,
      actorTimeInMailboxMetric.refine(actorTags),
      actorProcessingTimeMetric.refine(actorTags),
      actorMailboxSizeMetric.refine(actorTags),
      actorErrorsMetric.refine(actorTags)
    )
  }

  case class ActorMetrics(tags: Map[String, String], timeInMailbox: Histogram, processingTime: Histogram,
    mailboxSize: RangeSampler, errors: Counter) {

    def cleanup(): Unit = {
      actorTimeInMailboxMetric.remove(tags)
      actorProcessingTimeMetric.remove(tags)
      actorMailboxSizeMetric.remove(tags)
      actorErrorsMetric.remove(tags)
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

  def forRouter(path: String, system: String, dispatcher: String, actorClass: String): RouterMetrics = {
    val routerTags = Map("path" -> path, "system" -> system, "dispatcher" -> dispatcher)
    RouterMetrics(
      routerTags,
      routerRoutingTime.refine(routerTags),
      routerTimeInMailbox.refine(routerTags),
      routerProcessingTime.refine(routerTags),
      routerPendingMessages.refine(routerTags),
      routerMembers.refine(routerTags),
      routerErrors.refine(routerTags)
    )
  }

  case class RouterMetrics(tags: Map[String, String], routingTime: Histogram, timeInMailbox: Histogram,
      processingTime: Histogram, pendingMessages: RangeSampler, members: RangeSampler, errors: Counter) {

    def cleanup(): Unit = {
      routerRoutingTime.remove(tags)
      routerTimeInMailbox.remove(tags)
      routerProcessingTime.remove(tags)
      routerErrors.remove(tags)
      routerPendingMessages.remove(tags)
      routerMembers.remove(tags)
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
  val groupMailboxSize = Kamon.rangeSampler("akka.group.mailbox-size")
  val groupMembers = Kamon.rangeSampler("akka.group.members")
  val groupErrors = Kamon.counter("akka.group.errors")

  def forGroup(group: String, system: String): ActorGroupMetrics = {
    val actorTags = Map("group" -> group, "system" -> system)
    ActorGroupMetrics(
      actorTags,
      groupTimeInMailbox.refine(actorTags),
      groupProcessingTime.refine(actorTags),
      groupMailboxSize.refine(actorTags),
      groupMembers.refine(actorTags),
      groupErrors.refine(actorTags)
    )
  }

  case class ActorGroupMetrics(tags: Map[String, String], timeInMailbox: Histogram, processingTime: Histogram,
      mailboxSize: RangeSampler, members: RangeSampler, errors: Counter) {

    def cleanup(): Unit = {
      groupTimeInMailbox.remove(tags)
      groupProcessingTime.remove(tags)
      groupMailboxSize.remove(tags)
      groupMembers.remove(tags)
      groupErrors.remove(tags)
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
    val systemTags = Map("system" -> name)
    ActorSystemMetrics(
      systemTags,
      systemDeadLetters.refine(systemTags),
      systemUnhandledMessages.refine(systemTags),
      systemProcessedMessages.refine(systemTags + ("tracked" -> "true")),
      systemProcessedMessages.refine(systemTags + ("tracked" -> "false")),
      systemActiveActors.refine(systemTags)
    )
  }

  case class ActorSystemMetrics(tags: Map[String, String], deadLetters: Counter, unhandledMessages: Counter,
      processedMessagesByTracked: Counter, processedMessagesByNonTracked: Counter, activeActors: RangeSampler) {

    def cleanup(): Unit = {
      systemDeadLetters.remove(tags)
      systemUnhandledMessages.remove(tags)
      systemActiveActors.remove(tags)
      systemProcessedMessages.remove(tags + ("tracked" -> "true"))
      systemProcessedMessages.remove(tags + ("tracked" -> "false"))

    }
  }
}