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

package kamon.influxdb

class MetricDataPacketBuilder(maxPacketSizeInBytes: Long, flushToDestination: String ⇒ Any) {
  protected val metricSeparator = "\n"
  protected var buffer = new StringBuilder()

  protected def createInfluxString(data: TraversableOnce[(String, Any)]): String =
    data.map { case (k, v) ⇒ s"$k=$v" }.mkString(",")

  def appendMeasurement(key: String, tags: TraversableOnce[(String, String)], measurementData: TraversableOnce[(String, BigDecimal)], timeStamp: Long): Unit = {
    val tagsString = createInfluxString(tags)
    val valuesString = createInfluxString(measurementData)
    val data = s"$key,$tagsString $valuesString $timeStamp$metricSeparator"

    if (!fitsOnBuffer(data)) flush()

    buffer.append(data)
  }

  protected def fitsOnBuffer(data: String): Boolean = (buffer.length + data.length) <= maxPacketSizeInBytes

  def flush(): Unit = {
    flushToDestination(buffer.toString())
    buffer.clear()
  }
}
