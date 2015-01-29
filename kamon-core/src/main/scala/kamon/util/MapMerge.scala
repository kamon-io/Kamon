package kamon.util

object MapMerge {

  /**
   *  Merge to immutable maps with the same key and value types, using the provided valueMerge function.
   */
  implicit class Syntax[K, V](val map: Map[K, V]) extends AnyVal {
    def merge(that: Map[K, V], valueMerge: (V, V) ⇒ V): Map[K, V] = {
      val merged = Map.newBuilder[K, V]

      map.foreach {
        case (key, value) ⇒
          val mergedValue = that.get(key).map(v ⇒ valueMerge(value, v)).getOrElse(value)
          merged += key -> mergedValue
      }

      that.foreach {
        case kv @ (key, _) if !map.contains(key) ⇒ merged += kv
        case other                               ⇒ // ignore, already included.
      }

      merged.result();
    }
  }

}
