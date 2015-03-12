/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.util

object MapMerge {

  /**
   *  Merge two immutable maps with the same key and value types, using the provided valueMerge function.
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
