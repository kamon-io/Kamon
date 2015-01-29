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

import akka.testkit.{ ImplicitSender, TestKitBase }
import akka.actor.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }
import kamon.Kamon
import kamon.metric.{ SubscriptionsDispatcher, EntitySnapshot, MetricsExtensionImpl }
import kamon.trace.TraceContext
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

abstract class BaseKamonSpec(actorSystemName: String) extends TestKitBase with WordSpecLike with Matchers with ImplicitSender with BeforeAndAfterAll {
  lazy val kamon = Kamon(actorSystemName, config)
  lazy val collectionContext = kamon.metrics.buildDefaultCollectionContext
  implicit lazy val system: ActorSystem = kamon.actorSystem

  def config: Config =
    ConfigFactory.load()

  def newContext(name: String): TraceContext =
    kamon.tracer.newContext(name)

  def newContext(name: String, token: String): TraceContext =
    kamon.tracer.newContext(name, token)

  def takeSnapshotOf(name: String, category: String): EntitySnapshot = {
    val recorder = kamon.metrics.find(name, category).get
    recorder.collect(collectionContext)
  }

  def flushSubscriptions(): Unit =
    system.actorSelection("/user/kamon/subscriptions-dispatcher") ! SubscriptionsDispatcher.Tick

  override protected def afterAll(): Unit = system.shutdown()
}
