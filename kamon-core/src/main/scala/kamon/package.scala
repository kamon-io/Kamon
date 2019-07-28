/*
 *  ==========================================================================================
 *  Copyright © 2013-2019 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{Executors, ThreadFactory}

import com.typesafe.config.{Config, ConfigUtil}

import scala.collection.concurrent.TrieMap

package object kamon {

  /**
    * Creates a thread factory that assigns the specified name to all created Threads.
    */
  def threadFactory(name: String, daemon: Boolean = false): ThreadFactory =
    new ThreadFactory {
      val defaultFactory = Executors.defaultThreadFactory()

      override def newThread(r: Runnable): Thread = {
        val thread = defaultFactory.newThread(r)
        thread.setName(name)
        thread.setDaemon(daemon)
        thread
      }
    }

  def numberedThreadFactory(name: String, daemon: Boolean = false): ThreadFactory =
    new ThreadFactory {
      val count = new AtomicLong()
      val defaultFactory = Executors.defaultThreadFactory()

      override def newThread(r: Runnable): Thread = {
        val thread = defaultFactory.newThread(r)
        thread.setName(name + "-" + count.incrementAndGet().toString)
        thread.setDaemon(daemon)
        thread
      }
    }


  /**
    *  Workaround to the non thread-safe [scala.collection.concurrent.TrieMap#getOrElseUpdate()] method. More details on
    *  why this is necessary can be found at [[https://issues.scala-lang.org/browse/SI-7943]].
    */
  implicit class AtomicGetOrElseUpdateOnTrieMap[K, V](val trieMap: TrieMap[K, V]) extends AnyVal {

    def atomicGetOrElseUpdate(key: K, op: => V): V =
      atomicGetOrElseUpdate(key, op, { _: V => () }, { _: V => () })

    def atomicGetOrElseUpdate(key: K, op: => V, cleanup: V => Unit, init: V => Unit): V =
      trieMap.get(key) match {
        case Some(v) => v
        case None =>
          val d = op
          trieMap.putIfAbsent(key, d) match {
            case Some(oldValue) =>
              // If there was an old value then `d` was never added
              // and thus need to be cleanup.
              cleanup(d)
              oldValue

            case None =>
              init(d)
              d
          }
      }
  }

  implicit class UtilsOnConfig(val config: Config) extends AnyVal {
    import scala.collection.JavaConverters._

    def topLevelKeys: Set[String] =
      config.root().entrySet().asScala.map(_.getKey).toSet

    def configurations: Map[String, Config] = {
      topLevelKeys
        .map(entry => (entry, config.getConfig(ConfigUtil.joinPath(entry))))
        .toMap
    }

    def pairs: Map[String, String] = {
      topLevelKeys
        .map(key => (key, config.getString(key)))
        .toMap
    }
  }
}
