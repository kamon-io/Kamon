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

package kamon.metrics

import com.typesafe.config.Config
import kamon.metrics.instruments.{ CounterRecorder, ContinuousHdrRecorder }
import org.HdrHistogram.HdrRecorder

case class ActorMetrics(name: String) extends MetricGroupIdentity {
  val category = ActorMetrics
}

object ActorMetrics extends MetricGroupCategory {
  val name = "actor"

  case object ProcessingTime extends MetricIdentity { val name, tag = "processing-time" }
  case object MailboxSize extends MetricIdentity { val name, tag = "mailbox-size" }
  case object TimeInMailbox extends MetricIdentity { val name, tag = "time-in-mailbox" }
  case object ErrorCounter extends MetricIdentity { val name, tag = "errors" }

  case class ActorMetricRecorder(processingTime: MetricRecorder, mailboxSize: MetricRecorder, timeInMailbox: MetricRecorder, errorCounter: MetricRecorder)
      extends MetricGroupRecorder {

    def collect: MetricGroupSnapshot = {
      ActorMetricSnapshot(processingTime.collect(), mailboxSize.collect(), timeInMailbox.collect(), errorCounter.collect())
    }
  }

  case class ActorMetricSnapshot(processingTime: MetricSnapshotLike, mailboxSize: MetricSnapshotLike, timeInMailbox: MetricSnapshotLike, errorCounter: MetricSnapshotLike)
      extends MetricGroupSnapshot {

    val metrics: Map[MetricIdentity, MetricSnapshotLike] = Map(
      (ProcessingTime -> processingTime),
      (MailboxSize -> mailboxSize),
      (TimeInMailbox -> timeInMailbox),
      (ErrorCounter -> errorCounter))
  }

  val Factory = new MetricGroupFactory {
    type GroupRecorder = ActorMetricRecorder

    def create(config: Config): ActorMetricRecorder = {
      val settings = config.getConfig("kamon.metrics.precision.actor")

      val processingTimeConfig = extractPrecisionConfig(settings.getConfig("processing-time"))
      val mailboxSizeConfig = extractPrecisionConfig(settings.getConfig("mailbox-size"))
      val timeInMailboxConfig = extractPrecisionConfig(settings.getConfig("time-in-mailbox"))

      new ActorMetricRecorder(
        HdrRecorder(processingTimeConfig.highestTrackableValue, processingTimeConfig.significantValueDigits, Scale.Nano),
        ContinuousHdrRecorder(mailboxSizeConfig.highestTrackableValue, mailboxSizeConfig.significantValueDigits, Scale.Unit),
        HdrRecorder(timeInMailboxConfig.highestTrackableValue, timeInMailboxConfig.significantValueDigits, Scale.Nano),
        CounterRecorder())
    }
  }
}
