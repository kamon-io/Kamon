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
  val actorMailboxSizeMetric = Kamon.minMaxCounter("akka.actor.mailbox-size")
  val actorErrorsMetric = Kamon.counter("akka.actor.errors")

  def forActor(path: String, system: String, dispatcher: String): ActorMetrics = {
    val actorTags = Map("path" -> path, "system" -> system, "dispatcher" -> dispatcher)
    ActorMetrics(
      actorTags,
      actorTimeInMailboxMetric.refine(actorTags),
      actorProcessingTimeMetric.refine(actorTags),
      actorMailboxSizeMetric.refine(actorTags),
      actorErrorsMetric.refine(actorTags)
    )
  }

  case class ActorMetrics(tags: Map[String, String], timeInMailbox: Histogram, processingTime: Histogram,
    mailboxSize: MinMaxCounter, errors: Counter) {

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
    */
  val routerRoutingTime = Kamon.histogram("akka.router.routing-time", time.nanoseconds)
  val routerTimeInMailbox = Kamon.histogram("akka.router.time-in-mailbox", time.nanoseconds)
  val routerProcessingTime = Kamon.histogram("akka.router.processing-time", time.nanoseconds)
  val routerErrors = Kamon.counter("akka.router.errors")

  def forRouter(path: String, system: String, dispatcher: String): RouterMetrics = {
    val routerTags = Map("path" -> path, "system" -> system, "dispatcher" -> dispatcher)
    RouterMetrics(
      routerTags,
      routerRoutingTime.refine(routerTags),
      routerTimeInMailbox.refine(routerTags),
      routerProcessingTime.refine(routerTags),
      routerErrors.refine(routerTags)
    )
  }

  case class RouterMetrics(tags: Map[String, String], routingTime: Histogram, timeInMailbox: Histogram,
      processingTime: Histogram, errors: Counter) {

    def cleanup(): Unit = {
      routerRoutingTime.remove(tags)
      routerTimeInMailbox.remove(tags)
      routerProcessingTime.remove(tags)
      routerErrors.remove(tags)
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
  val groupMailboxSize = Kamon.minMaxCounter("akka.group.mailbox-size")
  val groupMembers = Kamon.minMaxCounter("akka.group.members")
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
      mailboxSize: MinMaxCounter, members: MinMaxCounter, errors: Counter) {

    def cleanup(): Unit = {
      groupTimeInMailbox.remove(tags)
      groupProcessingTime.remove(tags)
      groupMailboxSize.remove(tags)
      groupMembers.remove(tags)
      groupErrors.remove(tags)
    }
  }
}