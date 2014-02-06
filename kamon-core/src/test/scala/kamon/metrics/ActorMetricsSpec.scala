/* =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.metrics

import org.scalatest.{ WordSpecLike, Matchers }
import akka.testkit.TestKitBase
import akka.actor.{ Actor, Props, ActorSystem }
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import kamon.Kamon
import kamon.metrics.Subscriptions.TickMetricSnapshot

class ActorMetricsSpec extends TestKitBase with WordSpecLike with Matchers {
  implicit lazy val system: ActorSystem = ActorSystem("actor-metrics-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  filters = [
      |    {
      |      actor {
      |        includes = [ "user/*" ]
      |        excludes = [ ]
      |      }
      |    }
      |  ]
      |}
    """.stripMargin))

  "the Kamon actor metrics" should {
    "track configured actors" in {
      Kamon(Metrics).subscribe(ActorMetrics, "user/test-tracked-actor", testActor)

      system.actorOf(Props[Discard], "test-tracked-actor") ! "nothing"

      println(within(5 seconds) {
        expectMsgType[TickMetricSnapshot]
      })
    }
  }
}

class Discard extends Actor {
  def receive = { case a ⇒ }
}
