/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.akka

import java.nio.LongBuffer

import akka.actor._
import akka.routing.RoundRobinPool
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.{Entity, EntitySnapshot}
import kamon.metric.instrument.CollectionContext
import kamon.testkit.BaseKamonSpec

import scala.concurrent.duration._

class ActorGroupMetricsSpec extends BaseKamonSpec("actor-group-metrics-spec") {

  "the Kamon actor-group metrics" should {
    "respect the configured include and exclude filters for actors" in new ActorGroupMetricsFixtures {
      val metric = Kamon.metrics.entity(ActorGroupMetrics,
          Entity("tracked-group", ActorGroupMetrics.category)).asInstanceOf[ActorGroupMetrics]
      metric.collect(collectionContext)
      val trackedActor = createTestActor("tracked-actor")
      val nonTrackedActor = createTestActor("non-tracked-actor")
      system.stop(trackedActor)
      system.stop(nonTrackedActor)
      max(metric.collect(collectionContext), "actors") shouldBe 1
      val trackedActor2 = createTestActor("tracked-actor2")
      val trackedActor3 = createTestActor("tracked-actor3")
      system.stop(trackedActor2)
      system.stop(trackedActor3)
      max(metric.collect(collectionContext), "actors") shouldBe 2
    }
    "respect the configured include and exclude filters for routees" in new ActorGroupMetricsFixtures {
      val metric = Kamon.metrics.entity(ActorGroupMetrics,
          Entity("tracked-group", ActorGroupMetrics.category)).asInstanceOf[ActorGroupMetrics]
      metric.collect(collectionContext)
      val trackedRouter = createTestPoolRouter("tracked-router")
      val nonTrackedRouter = createTestPoolRouter("non-tracked-router")
      system.stop(trackedRouter)
      system.stop(nonTrackedRouter)
      max(metric.collect(collectionContext), "actors") shouldBe 5
      val trackedActor2 = createTestPoolRouter("tracked-router2")
      val trackedActor3 = createTestPoolRouter("tracked-router3")
      system.stop(trackedActor2)
      system.stop(trackedActor3)
      max(metric.collect(collectionContext), "actors") shouldBe 10
    }
  }

  override protected def afterAll(): Unit = shutdown()

  trait ActorGroupMetricsFixtures {
    val collectionContext = new CollectionContext {
      override val buffer: LongBuffer = LongBuffer.allocate(10000)
    }

    def createTestActor(name: String): ActorRef = {
      val actor = system.actorOf(Props[ActorMetricsTestActor], name)
      val initialiseListener = TestProbe()

      // Ensure that the router has been created before returning.
      actor.tell(ActorMetricsTestActor.Ping, initialiseListener.ref)
      initialiseListener.expectMsg(ActorMetricsTestActor.Pong)

      actor
    }

    def createTestPoolRouter(routerName: String): ActorRef = {
      val router = system.actorOf(RoundRobinPool(5).props(Props[RouterMetricsTestActor]), routerName)
      val initialiseListener = TestProbe()

      // Ensure that the router has been created before returning.
      router.tell(RouterMetricsTestActor.Ping, initialiseListener.ref)
      initialiseListener.expectMsg(RouterMetricsTestActor.Pong)

      router
    }

    def max(snapshot: EntitySnapshot, counterName: String): Long =
      snapshot.minMaxCounter(counterName).map(_.max).getOrElse(fail(s"Unknown counter $counterName"))
  }
}
