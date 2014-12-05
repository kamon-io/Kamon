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

import kamon.Timestamp
import spray.json._

object JsonProtocol extends DefaultJsonProtocol {

  implicit object ConnectJsonWriter extends RootJsonWriter[AgentSettings] {
    def write(obj: AgentSettings): JsValue =
      JsArray(
        JsObject(
          "agent_version" -> JsString("3.1.0"),
          "app_name" -> JsArray(JsString(obj.appName)),
          "host" -> JsString(obj.hostname),
          "identifier" -> JsString(s"java:${obj.appName}"),
          "language" -> JsString("java"),
          "pid" -> JsNumber(obj.pid)))
  }

  implicit def seqWriter[T: JsonFormat] = new JsonFormat[Seq[T]] {
    def read(value: JsValue): Seq[T] = value match {
      case JsArray(elements) ⇒ elements.map(_.convertTo[T])(collection.breakOut)
      case x                 ⇒ deserializationError("Expected Seq as JsArray, but got " + x)
    }

    def write(seq: Seq[T]) = JsArray(seq.map(_.toJson).toVector)
  }

  implicit object MetricDetailWriter extends JsonFormat[Metric] {
    def read(json: JsValue): (MetricID, MetricData) = json match {
      case JsArray(elements) ⇒
        val metricID = elements(0) match {
          case JsObject(fields) ⇒ MetricID(fields("name").convertTo[String], fields.get("scope").map(_.convertTo[String]))
          case x                ⇒ deserializationError("Expected MetricID as JsObject, but got " + x)
        }

        val metricData = elements(1) match {
          case JsArray(dataElements) ⇒
            MetricData(
              dataElements(0).convertTo[Long],
              dataElements(1).convertTo[Double],
              dataElements(2).convertTo[Double],
              dataElements(3).convertTo[Double],
              dataElements(4).convertTo[Double],
              dataElements(5).convertTo[Double])
          case x ⇒ deserializationError("Expected MetricData as JsArray, but got " + x)
        }

        (metricID, metricData)

      case x ⇒ deserializationError("Expected Metric as JsArray, but got " + x)
    }

    def write(obj: Metric): JsValue = {
      val (metricID, metricData) = obj
      val nameAndScope = metricID.scope.foldLeft(Map("name" -> JsString(metricID.name)))((m, scope) ⇒ m + ("scope" -> JsString(scope)))

      JsArray(
        JsObject(nameAndScope),
        JsArray(
          JsNumber(metricData.callCount),
          JsNumber(metricData.total),
          JsNumber(metricData.totalExclusive),
          JsNumber(metricData.min),
          JsNumber(metricData.max),
          JsNumber(metricData.sumOfSquares)))
    }
  }

  implicit object MetricBatchWriter extends RootJsonFormat[MetricBatch] {

    def read(json: JsValue): MetricBatch = json match {
      case JsArray(elements) ⇒
        val runID = elements(0).convertTo[Long]
        val timeSliceFrom = new Timestamp(elements(1).convertTo[Long])
        val timeSliceTo = new Timestamp(elements(2).convertTo[Long])
        val metrics = elements(3).convertTo[Seq[Metric]]

        MetricBatch(runID, TimeSliceMetrics(timeSliceFrom, timeSliceTo, metrics.toMap))

      case x ⇒ deserializationError("Expected Array as JsArray, but got " + x)
    }

    def write(obj: MetricBatch): JsValue =
      JsArray(
        JsNumber(obj.runID),
        JsNumber(obj.timeSliceMetrics.from.seconds),
        JsNumber(obj.timeSliceMetrics.to.seconds),
        obj.timeSliceMetrics.metrics.toSeq.toJson)
  }
}
