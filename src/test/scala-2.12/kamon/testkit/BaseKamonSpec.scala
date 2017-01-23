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

package kamon.testkit

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKitBase }
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.{ Entity, EntitySnapshot, SubscriptionsDispatcher }
import kamon.trace.TraceContext
import kamon.util.LazyActorRef
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

abstract class BaseKamonSpec(actorSystemName: String) extends TestKitBase with WordSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {
  lazy val collectionContext = Kamon.metrics.buildDefaultCollectionContext
  implicit lazy val system: ActorSystem = {
    Kamon.start()
    ActorSystem(actorSystemName, config)
  }

  def config: Config =
    Kamon.config

  def newContext(name: String): TraceContext =
    Kamon.tracer.newContext(name)

  def newContext(name: String, token: String): TraceContext =
    Kamon.tracer.newContext(name, Option(token))

  def newContext(name: String, token: String, tags: Map[String, String]): TraceContext =
    Kamon.tracer.newContext(name, Option(token), tags)

  def takeSnapshotOf(name: String, category: String): EntitySnapshot = {
    val recorder = Kamon.metrics.find(name, category).get
    recorder.collect(collectionContext)
  }

  def takeSnapshotOf(name: String, category: String, tags: Map[String, String]): EntitySnapshot = {
    val recorder = Kamon.metrics.find(Entity(name, category, tags)).get
    recorder.collect(collectionContext)
  }

  def flushSubscriptions(): Unit = {
    val subscriptionsField = Kamon.metrics.getClass.getDeclaredField("_subscriptions")
    subscriptionsField.setAccessible(true)
    val subscriptions = subscriptionsField.get(Kamon.metrics).asInstanceOf[LazyActorRef]

    subscriptions.tell(SubscriptionsDispatcher.Tick)
  }

  override protected def afterAll(): Unit = system.terminate()
}