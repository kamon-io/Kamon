/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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
import kamon.metrics.instruments.ContinuousHighDynamicRangeRecorder
import org.HdrHistogram.HighDynamicRangeRecorder

object ActorMetrics extends MetricGroupIdentity.Category with MetricGroupFactory {
  type GroupRecorder = ActorMetricRecorder
  val entityName = "actor"

  case object ProcessingTime extends MetricIdentity { val name, tag = "ProcessingTime" }
  case object MailboxSize extends MetricIdentity { val name, tag = "MailboxSize" }
  case object TimeInMailbox extends MetricIdentity { val name, tag = "TimeInMailbox" }

  case class ActorMetricRecorder(processingTime: MetricRecorder, mailboxSize: MetricRecorder, timeInMailbox: MetricRecorder)
      extends MetricGroupRecorder {

    def record(identity: MetricIdentity, value: Long): Unit = identity match {
      case ProcessingTime ⇒ processingTime.record(value)
      case MailboxSize    ⇒ mailboxSize.record(value)
      case TimeInMailbox  ⇒ timeInMailbox.record(value)
    }

    def collect: MetricGroupSnapshot = {
      ActorMetricSnapshot(processingTime.collect(), mailboxSize.collect(), timeInMailbox.collect())
    }
  }

  case class ActorMetricSnapshot(processingTime: MetricSnapshot, mailboxSize: MetricSnapshot, timeInMailbox: MetricSnapshot)
      extends MetricGroupSnapshot {

    def metrics: Map[MetricIdentity, MetricSnapshot] = Map(
      (ProcessingTime -> processingTime),
      (MailboxSize -> mailboxSize),
      (TimeInMailbox -> timeInMailbox))
  }

  def create(config: Config): ActorMetricRecorder = {
    import HighDynamicRangeRecorder.Configuration

    val settings = config.getConfig("kamon.metrics.precision.actor")
    val processingTimeHdrConfig = Configuration.fromConfig(settings.getConfig("processing-time"))
    val mailboxSizeHdrConfig = Configuration.fromConfig(settings.getConfig("mailbox-size"))
    val timeInMailboxHdrConfig = Configuration.fromConfig(settings.getConfig("time-in-mailbox"))

    new ActorMetricRecorder(
      HighDynamicRangeRecorder(processingTimeHdrConfig),
      ContinuousHighDynamicRangeRecorder(mailboxSizeHdrConfig),
      HighDynamicRangeRecorder(timeInMailboxHdrConfig))
  }
}
