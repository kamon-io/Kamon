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

object AgentJsonProtocol extends DefaultJsonProtocol {

  implicit object ConnectJsonWriter extends RootJsonWriter[AgentInfo] {
    def write(obj: AgentInfo): JsValue =
      JsArray(
        JsObject(
          "agent_version" -> JsString("3.1.0"),
          "app_name"      -> JsArray(JsString(obj.appName)),
          "host"          -> JsString(obj.host),
          "identifier"    -> JsString(s"java:${obj.appName}"),
          "language"      -> JsString("java"),
          "pid"           -> JsNumber(obj.pid)
        )
      )
  }

  import NewRelicMetric._

  implicit def listWriter[T : JsonWriter] = new JsonWriter[List[T]] {
    def write(list: List[T]) = JsArray(list.map(_.toJson))
  }

  implicit object MetricDetailWriter extends JsonWriter[(ID, Data)] {
    def write(obj: (ID, Data)): JsValue = {
      val (id, data) = obj
      JsArray(
        JsObject(
          "name" -> JsString(id.name) // TODO Include scope
        ),
        JsArray(
          JsNumber(data.callCount),
          JsNumber(data.total),
          JsNumber(data.totalExclusive),
          JsNumber(data.min),
          JsNumber(data.max),
          JsNumber(data.sumOfSquares)
        )
      )
    }
  }

  implicit object MetricDataWriter extends RootJsonWriter[MetricData] {
    def write(obj: MetricData): JsValue =
      JsArray(
        JsNumber(obj.runId),
        JsNumber(obj.start),
        JsNumber(obj.end),
        obj.metrics.toJson
      )
  }
}
