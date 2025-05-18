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

package kamon.instrumentation.pekko

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.AskPattern.Askable
import org.apache.pekko.util.Timeout
import kamon.instrumentation.pekko.ActorMetricsTestActor._
import kamon.instrumentation.pekko.PekkoMetrics._
import kamon.instrumentation.pekko.TypedActor._
import kamon.tag.TagSet
import kamon.testkit.{InitAndStopKamonAfterAll, InstrumentInspection, MetricInspection}
import org.scalactic.TimesOnInt._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class TypedActorMetricsSpec extends AnyWordSpec
    with MetricInspection.Syntax with InstrumentInspection.Syntax with Matchers
    with InitAndStopKamonAfterAll {

  private val system: ActorSystem[Greet] = ActorSystem(TypedActor(), "greet-service")

  "the Kamon typed actor metrics" should {
    "respect the configured include and exclude filters" in new ActorMetricsFixtures {
      sendMessage("world")
      ActorProcessingTime.tagValues("path") should contain("greet-service/user")
    }
  }

  override protected def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  trait ActorMetricsFixtures {

    def sendMessage(name: String): Unit = {
      val fut = system.ask[Greeted](Greet(name, _))(Timeout(10.seconds), system.scheduler)
      Await.result(fut, 10.seconds)
    }
  }
}
