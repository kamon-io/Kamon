/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

import com.grack.nanojson.JsonObject

case class DdSpan(
  traceId: BigInt,
  spanId: BigInt,
  parentId: Option[BigInt],
  name: String,
  resource: String,
  service: String,
  spanType: String,
  start: Long,
  duration: Duration,
  meta: Map[String, String],
  error: Boolean
) {

  def toJson(): JsonObject = {
    val metaBuilder = JsonObject.builder
    meta.foreach { case (k, v) =>
      metaBuilder.value(k, v)
    }
    val metaObj = metaBuilder.done

    val json = JsonObject.builder
      .value("trace_id", BigDecimal(traceId))
      .value("span_id", BigDecimal(spanId))
      .value("name", name)
      .value("type", spanType)
      .value("resource", resource)
      .value("service", service)
      .value("start", BigDecimal(start))
      .value("duration", BigDecimal(duration.toNanos))
      .`object`("meta", metaObj)
      .value("error", if (error) 1 else 0)
      .`object`("metrics")
      // This tells the datadog agent to keep the trace. We've already determined sampling here or we wouldn't
      // be in this method. Keep in mind this DOES NOT respect sampling rates in the datadog agent
      // https://docs.datadoghq.com/tracing/guide/trace_sampling_and_storage/#client-implementation
      .value("_sampling_priority_v1", 1)
      .end

    if (parentId.nonEmpty) {
      json.value("parent_id", BigDecimal(parentId.get)).done
    } else {
      json.done
    }
  }
}
