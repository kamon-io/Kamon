/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.newrelic

import akka.util.Timeout
import org.scalatest.{ Matchers, WordSpecLike }
import scala.concurrent.duration.DurationInt
import spray.json.JsValue

class ConnectJsonWriterSpec extends WordSpecLike with Matchers {
  import kamon.newrelic.JsonProtocol._

  "the ConnectJsonWriter" should {
    "produce the correct Json when a single app name is configured" in {
      ConnectJsonWriter.write(agentSettings("app1")).compactPrint shouldBe expectedJson(""""app1"""")
    }

    "produce the correct Json when multiple app names are configured" in {
      ConnectJsonWriter.write(agentSettings("app1;app2;app3")).compactPrint shouldBe expectedJson(""""app1","app2","app3"""");
    }
  }

  def agentSettings(appName: String) = AgentSettings("1111111111", appName, "test-host", 1, Timeout(5 seconds), 1, 30 seconds, 1D)

  def expectedJson(appName: String) = s"""[{"identifier":"java:app1","agent_version":"3.1.0","host":"test-host","pid":1,"language":"java","app_name":[$appName]}]"""
}