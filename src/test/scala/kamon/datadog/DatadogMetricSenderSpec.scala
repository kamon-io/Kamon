/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.datadog


import java.time.Instant

import kamon.Kamon
import kamon.datadog.DatadogAgentReporter.PacketBuffer
import kamon.metric._
import kamon.testkit.Reconfigure
import org.scalatest.{Matchers, WordSpec}

class DatadogMetricSenderSpec extends WordSpec with Matchers with Reconfigure {
  reconfigure =>

  def testBuffer() = new PacketBuffer {

    val lst = scala.collection.mutable.Map.empty[String, String]

    override def flush(): Unit = {}
    override def appendMeasurement(
      key: String,
      measurementData: String
    ): Unit = {
      lst += (key -> measurementData)
    }
  }

  "the DataDogMetricSender" should {
    "send counter metrics" in AgentReporter(testBuffer()) { case (buffer, reporter) =>

      val now = Instant.now()
      reporter.reportPeriodSnapshot(
        PeriodSnapshot.apply(
          now.minusMillis(1000), now, MetricsSnapshot.apply(
            Nil,
            Nil,
            Nil,
            Seq(
              MetricValue.apply("test.counter", Map("tag1" -> "value1"), MeasurementUnit.none, 0)
            )

          )
        )
      )

      buffer.lst should have size (1)
      println(buffer.lst)
      buffer.lst should contain ("test.counter" -> "0|c#|service:kamon-application,tag1:value1")
    }

  }

  def AgentReporter[A, B <: PacketBuffer](buffer: B)(f: (B, DatadogAgentReporter) => A): A = {
    this.resetConfig()
    // Overriding a class for testing is an anti-pattern. But it's the best I could do
    val reporter = new DatadogAgentReporter(DatadogAgentReporter.readConfiguration(Kamon.config()).copy(packetBuffer = buffer))
    f(buffer, reporter)
  }
}
