/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.trace

import java.util

import io.opentracing.propagation.TextMap
import org.scalatest.{Matchers, WordSpecLike}


class SpanContextCodecSpec extends WordSpecLike with Matchers {
  "The Span Context Codec" should {

    "supports Text Map extraction" in {
      val textMap = MapTextMap()
      textMap.put("TRACE_ID", "1")
      textMap.put("PARENT_ID", "2")
      textMap.put("SPAN_ID", "3")
      textMap.put("SAMPLED", "sampled")
      textMap.put("BAGGAGE_1", "awesome-baggage-1")
      textMap.put("BAGGAGE_2", "awesome-baggage-2")

      val spanContext = SpanContextCodec.TextMap.extract(textMap, Sampler.never)

      spanContext.traceID should be(1)
      spanContext.parentID should be(2)
      spanContext.spanID should be(3)
      spanContext.sampled should be(false)
      spanContext.baggageMap should be(Map("1" -> "awesome-baggage-1", "2" -> "awesome-baggage-2"))
    }

    "supports Text Map injection" in {
      val textMap = MapTextMap()

      SpanContextCodec.TextMap.inject(new SpanContext(1, 2, 3, false, Map("MDC" -> "awesome-mdc-value")), textMap)

      textMap.map.get("TRACE_ID") should be("0000000000000001")
      textMap.map.get("PARENT_ID") should be("0000000000000003")
      textMap.map.get("SPAN_ID") should be("0000000000000002")
      textMap.map.get("SAMPLED") should be(null)
      textMap.map.get("BAGGAGE_MDC") should be("awesome-mdc-value")
    }

    "supports Http Headers extraction" in {
      val textMap = MapTextMap()
      textMap.put("X-B3-TraceId", "1")
      textMap.put("X-B3-ParentSpanId", "2")
      textMap.put("X-B3-SpanId", "3")
      textMap.put("X-B3-Sampled", "sampled")
      textMap.put("X-B3-Baggage-1", "awesome-baggage-1")
      textMap.put("X-B3-Baggage-2", "awesome-baggage-2")

      val spanContext = SpanContextCodec.ZipkinB3.extract(textMap, Sampler.never)

      spanContext.traceID should be(1)
      spanContext.parentID should be(2)
      spanContext.spanID should be(3)
      spanContext.sampled should be(false)
      spanContext.baggageMap should be(Map("1" -> "awesome-baggage-1", "2" -> "awesome-baggage-2"))
    }

    "supports Http Headers injection" in {
      val textMap = MapTextMap()

      SpanContextCodec.ZipkinB3.inject(new SpanContext(1, 2, 3, false, Map("MDC" -> "awesome-mdc-value")), textMap)

      textMap.map.get("X-B3-TraceId") should be("0000000000000001")
      textMap.map.get("X-B3-ParentSpanId") should be("0000000000000003")
      textMap.map.get("X-B3-SpanId") should be("0000000000000002")
      textMap.map.get("X-B3-Sampled") should be(null)
      textMap.map.get("X-B3-Baggage-MDC") should be("awesome-mdc-value")
    }
  }
}

class MapTextMap extends TextMap {
  val map = new util.HashMap[String, String]()

  override def iterator: util.Iterator[util.Map.Entry[String, String]] =
    map.entrySet.iterator

  override def put(key: String, value: String): Unit = {
    map.put(key, value)
  }
}

object MapTextMap {
  def apply(): MapTextMap = new MapTextMap()
}



