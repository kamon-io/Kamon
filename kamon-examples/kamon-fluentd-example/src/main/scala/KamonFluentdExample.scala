/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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

import akka.actor.ActorSystem
import kamon.Kamon
import spray.routing.SimpleRoutingApp
import xerial.fluentd.{ FluentdConfig, FluentdStandalone }

import scala.util.Properties

object KamonFluentdExample extends App with SimpleRoutingApp {
  // Start fluentd server only for this sample app
  // In real usecase, you will spawn fluentd server independently.
  val fluentdServer = FluentdStandalone.start(FluentdConfig(configuration =
    """
      |<source>
      |  type forward
      |  port 24224
      |</source>
      |<match **>
      |  type file
      |  path target/fluentd-out
      |</match>
    """.stripMargin))
  sys.addShutdownHook {
    fluentdServer.stop
  }

  // start Kamon
  Kamon.start()

  implicit val system = ActorSystem("kamon-fluentd-example")

  // server endpoint
  val interface = "0.0.0.0"
  val port = Properties.envOrElse("PORT", "8080").toInt

  // resource endpoints
  startServer(interface = interface, port = port) {
    path("hello") {
      get {
        complete {
          <h1>Hello! Kamon Fluentd Example!</h1>
        }
      }
    }
  }
}
