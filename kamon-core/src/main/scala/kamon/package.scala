import com.typesafe.config.Config

import scala.collection.concurrent.TrieMap

package object kamon {



  /**
    *  Workaround to the non thread-safe [[scala.collection.concurrent.TrieMap#getOrElseUpdate]] method. More details on
    *  why this is necessary can be found at [[https://issues.scala-lang.org/browse/SI-7943]].
    */
  implicit class AtomicGetOrElseUpdateOnTrieMap[K, V](val trieMap: TrieMap[K, V]) extends AnyVal {

    def atomicGetOrElseUpdate(key: K, op: ⇒ V): V =
      atomicGetOrElseUpdate(key, op, { v: V ⇒ Unit })

    def atomicGetOrElseUpdate(key: K, op: ⇒ V, cleanup: V ⇒ Unit): V =
      trieMap.get(key) match {
        case Some(v) ⇒ v
        case None ⇒
          val d = op
          trieMap.putIfAbsent(key, d).map { oldValue ⇒
            // If there was an old value then `d` was never added
            // and thus need to be cleanup.
            cleanup(d)
            oldValue

          } getOrElse (d)
      }
  }


  implicit class UtilsOnConfig(val config: Config) extends AnyVal {
    import scala.collection.JavaConverters._

    def firstLevelKeys: Set[String] = {
      config.entrySet().asScala.map {
        case entry ⇒ entry.getKey.takeWhile(_ != '.')
      } toSet
    }

    def configurations: Map[String, Config] = {
      firstLevelKeys
        .map(entry => (entry, config.getConfig(entry)))
        .toMap
    }
  }
}
