/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.datadog

import java.time.Duration

import play.api.libs.json._

case class DdSpan(
  traceId:  BigInt,
  spanId:   BigInt,
  parentId: Option[BigInt],
  name:     String,
  resource: String,
  service:  String,
  spanType: String,
  start:    Long,
  duration: Duration,
  meta:     Map[String, String],
  error:    Boolean) {

  def toJson(): JsObject = {
    val json = JsObject(Map(
      "trace_id" -> JsNumber(BigDecimal(traceId)),
      "span_id" -> JsNumber(BigDecimal(spanId)),
      "name" -> JsString(name),
      "type" -> JsString(spanType),
      "resource" -> JsString(resource),
      "service" -> JsString(service),
      "start" -> JsNumber(BigDecimal(start)),
      "duration" -> JsNumber(BigDecimal(duration.toNanos)),
      "meta" -> JsObject(
        meta.mapValues(JsString(_)).toSeq
      ),
      "error" -> JsNumber(if (error) 1 else 0),
      "metrics" -> JsObject(Map(
        // This tells the datadog agent to keep the trace. We've already determined sampling here or we wouldn't
        // be in this method. Keep in mind this DOES NOT respect sampling rates in the datadog agent
        // https://docs.datadoghq.com/tracing/guide/trace_sampling_and_storage/#client-implementation
        "_sampling_priority_v1" -> JsNumber(1)
      ))
    ).toSeq)
    if (parentId.nonEmpty) {
      json + ("parent_id", JsNumber(BigDecimal(parentId.get)))
    } else {
      json
    }
  }
}
