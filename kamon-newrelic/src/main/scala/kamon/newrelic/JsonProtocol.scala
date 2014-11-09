/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.newrelic

import spray.json._
import kamon.newrelic.Agent._

object JsonProtocol extends DefaultJsonProtocol {

  implicit object ConnectJsonWriter extends RootJsonWriter[Settings] {
    def write(obj: Settings): JsValue =
      JsArray(
        JsObject(
          "agent_version" -> JsString("3.1.0"),
          "app_name" -> JsArray(JsString(obj.appName)),
          "host" -> JsString(obj.host),
          "identifier" -> JsString(s"java:${obj.appName}"),
          "language" -> JsString("java"),
          "pid" -> JsNumber(obj.pid)))
  }

  implicit def seqWriter[T: JsonWriter] = new JsonWriter[Seq[T]] {
    def write(seq: Seq[T]) = JsArray(seq.map(_.toJson).toVector)
  }

  implicit object MetricDetailWriter extends JsonWriter[Metric] {
    def write(obj: Metric): JsValue = {
      val (metricID, metricData) = obj

      JsArray(
        JsObject(
          "name" -> JsString(metricID.name) // TODO Include scope
          ),
        JsArray(
          JsNumber(metricData.callCount),
          JsNumber(metricData.total),
          JsNumber(metricData.totalExclusive),
          JsNumber(metricData.min),
          JsNumber(metricData.max),
          JsNumber(metricData.sumOfSquares)))
    }
  }

  implicit object MetricBatchWriter extends RootJsonWriter[MetricBatch] {
    def write(obj: MetricBatch): JsValue =
      JsArray(
        JsNumber(obj.runID),
        JsNumber(obj.timeSliceMetrics.from),
        JsNumber(obj.timeSliceMetrics.to),
        obj.timeSliceMetrics.metrics.toSeq.toJson)
  }
}
