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
package kamon

import org.scalatest.{ WordSpecLike, WordSpec }
import akka.testkit.TestKit
import akka.actor.{ Props, ActorSystem }

class MailboxSizeMetricsSpec extends TestKit(ActorSystem("mailbox-size-metrics-spec")) with WordSpecLike {

  "the mailbox size metrics instrumentation" should {
    "register a counter for mailbox size upon actor creation" in {
      val target = system.actorOf(Props.empty, "sample")

      //Metrics.registry.getHistograms.get("akka://mailbox-size-metrics-spec/sample:MAILBOX")
    }
  }
}
