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

import akka.actor.ActorSystem
import com.typesafe.config.Config
import kamon.metric._
import kamon.metric.instrument.{ Counter, Histogram, MinMaxCounter }

case class ActorMetrics(name: String) extends MetricGroupIdentity {
  val category = ActorMetrics
}

object ActorMetrics extends MetricGroupCategory {
  val name = "actor"

  case object ProcessingTime extends MetricIdentity { val name = "processing-time" }
  case object MailboxSize extends MetricIdentity { val name = "mailbox-size" }
  case object TimeInMailbox extends MetricIdentity { val name = "time-in-mailbox" }
  case object Errors extends MetricIdentity { val name = "errors" }

  case class ActorMetricsRecorder(processingTime: Histogram, timeInMailbox: Histogram, mailboxSize: MinMaxCounter,
      errors: Counter) extends MetricGroupRecorder {

    def collect(context: CollectionContext): ActorMetricSnapshot =
      ActorMetricSnapshot(
        processingTime.collect(context),
        timeInMailbox.collect(context),
        mailboxSize.collect(context),
        errors.collect(context))

    def cleanup: Unit = {
      processingTime.cleanup
      mailboxSize.cleanup
      timeInMailbox.cleanup
      errors.cleanup
    }
  }

  case class ActorMetricSnapshot(processingTime: Histogram.Snapshot, timeInMailbox: Histogram.Snapshot,
      mailboxSize: Histogram.Snapshot, errors: Counter.Snapshot) extends MetricGroupSnapshot {

    type GroupSnapshotType = ActorMetricSnapshot

    def merge(that: ActorMetricSnapshot, context: CollectionContext): ActorMetricSnapshot =
      ActorMetricSnapshot(
        processingTime.merge(that.processingTime, context),
        timeInMailbox.merge(that.timeInMailbox, context),
        mailboxSize.merge(that.mailboxSize, context),
        errors.merge(that.errors, context))

    lazy val metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      (ProcessingTime -> processingTime),
      (MailboxSize -> mailboxSize),
      (TimeInMailbox -> timeInMailbox),
      (Errors -> errors))
  }

  val Factory = ActorMetricGroupFactory
}

case object ActorMetricGroupFactory extends MetricGroupFactory {
  import kamon.akka.ActorMetrics._

  type GroupRecorder = ActorMetricsRecorder

  def create(config: Config, system: ActorSystem): ActorMetricsRecorder = {
    val settings = config.getConfig("precision.actor")

    val processingTimeConfig = settings.getConfig("processing-time")
    val timeInMailboxConfig = settings.getConfig("time-in-mailbox")
    val mailboxSizeConfig = settings.getConfig("mailbox-size")

    new ActorMetricsRecorder(
      Histogram.fromConfig(processingTimeConfig),
      Histogram.fromConfig(timeInMailboxConfig),
      MinMaxCounter.fromConfig(mailboxSizeConfig, system),
      Counter())
  }
}
