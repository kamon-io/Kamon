package kamon.trace

import java.lang
import java.util.Map
import scala.collection.JavaConverters._

class SpanContext(val traceID: Long, val spanID: Long, val parentID: Long) extends io.opentracing.SpanContext {
  private var baggage = scala.collection.immutable.Map.empty[String, String]

  private[kamon] def addBaggageItem(key: String, value: String): Unit = synchronized {
    baggage = baggage + (key -> value)
  }

  private[kamon] def getBaggage(key: String): String = synchronized {
    baggage.get(key).getOrElse(null)
  }

  private[kamon] def baggageMap: scala.collection.immutable.Map[String, String] =
    baggage

  override def baggageItems(): lang.Iterable[Map.Entry[String, String]] = synchronized {
    baggage.asJava.entrySet()
  }
}
