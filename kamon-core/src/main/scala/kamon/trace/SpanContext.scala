package kamon.trace

import java.lang
import java.util.{Map => JavaMap}

import scala.collection.JavaConverters._

class SpanContext(val traceID: Long, val spanID: Long, val parentID: Long, val sampled: Boolean,
  private var baggage: Map[String, String]) extends io.opentracing.SpanContext {

  private[kamon] def addBaggageItem(key: String, value: String): Unit = synchronized {
    baggage = baggage + (key -> value)
  }

  private[kamon] def getBaggage(key: String): String = synchronized {
    baggage.get(key).getOrElse(null)
  }

  private[kamon] def baggageMap: Map[String, String] =
    baggage

  override def baggageItems(): lang.Iterable[JavaMap.Entry[String, String]] = synchronized {
    baggage.asJava.entrySet()
  }
}
