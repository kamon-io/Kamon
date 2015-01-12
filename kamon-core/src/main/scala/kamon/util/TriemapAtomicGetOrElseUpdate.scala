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
