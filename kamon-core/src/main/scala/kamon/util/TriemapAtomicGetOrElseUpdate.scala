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

import scala.collection.concurrent.TrieMap

object TriemapAtomicGetOrElseUpdate {

  /**
   *  Workaround to the non thread-safe [[scala.collection.concurrent.TrieMap#getOrElseUpdate]] method. More details on
   *  why this is necessary can be found at [[https://issues.scala-lang.org/browse/SI-7943]].
   */
  implicit class Syntax[K, V](val trieMap: TrieMap[K, V]) extends AnyVal {
    def atomicGetOrElseUpdate(key: K, op: ⇒ V): V =
      trieMap.get(key) match {
        case Some(v) ⇒ v
        case None    ⇒ val d = op; trieMap.putIfAbsent(key, d).getOrElse(d)
      }
  }
}
