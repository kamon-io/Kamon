/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.trace.instrumentation

import org.scalatest.{ WordSpecLike, WordSpec }
import akka.testkit.{ TestKitBase, TestKit }
import akka.actor.ActorSystem
import scala.concurrent.duration._
import kamon.trace.UowTracing.{ Finish, Rename, Start }
import kamon.trace.{ UowTrace, UowTraceAggregator }

class TraceAggregatorSpec extends TestKit(ActorSystem("TraceAggregatorSpec")) with WordSpecLike {

  "a TraceAggregator" should {
    "send a UowTrace message out after receiving a Finish message" in new AggregatorFixture {
      within(1 second) {
        aggregator ! Start(1, "/accounts")
        aggregator ! Finish(1)

        //expectMsg(UowTrace("UNKNOWN", Seq(Start(1, "/accounts"), Finish(1))))
      }
    }

    "change the uow name after receiving a Rename message" in new AggregatorFixture {
      within(1 second) {
        aggregator ! Start(1, "/accounts")
        aggregator ! Rename(1, "test-uow")
        aggregator ! Finish(1)

        //expectMsg(UowTrace("test-uow", Seq(Start(1, "/accounts"), Finish(1))))
      }
    }
  }

  trait AggregatorFixture {
    val aggregator = system.actorOf(UowTraceAggregator.props(testActor, 10 seconds))
  }
}
