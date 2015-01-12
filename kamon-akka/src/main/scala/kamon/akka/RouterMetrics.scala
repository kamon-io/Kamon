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

import kamon.metric._
import kamon.metric.instrument.{ Time, InstrumentFactory }

/**
 *  Entity recorder for Akka Routers. The metrics being tracked are:
 *
 *    - routing-time: Time taken for the router to process the routing logic.
 *    - time-in-mailbox: Time spent from the instant when a message is enqueued in a actor's mailbox to the instant when
 *      that message is dequeued for processing.
 *    - processing-time: Time taken for the actor to process the receive function.
 *    - errors: Number or errors seen by the actor's supervision mechanism.
 */
class RouterMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val routingTime = histogram("routing-time", Time.Nanoseconds)
  val timeInMailbox = histogram("time-in-mailbox", Time.Nanoseconds)
  val processingTime = histogram("processing-time", Time.Nanoseconds)
  val errors = counter("errors")
}

object RouterMetrics extends EntityRecorderFactory[RouterMetrics] {
  def category: String = "akka-router"
  def createRecorder(instrumentFactory: InstrumentFactory): RouterMetrics = new RouterMetrics(instrumentFactory)
}