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
package kamon.akka.http.server

import kamon.http.HttpServerMetrics
import kamon.metric.EntityRecorderFactory
import kamon.metric.instrument.InstrumentFactory

class AkkaHttpServerMetrics(instrumentFactory: InstrumentFactory) extends HttpServerMetrics(instrumentFactory) {
  def recordConnection(bindingAddress: String): Unit =
    counter(bindingAddress).increment()

  def openConnections = minMaxCounter("open-connections")
}

object AkkaHttpServerMetrics extends EntityRecorderFactory[AkkaHttpServerMetrics] {
  def category: String = "akka-http-server"
  def createRecorder(instrumentFactory: InstrumentFactory): AkkaHttpServerMetrics =
    new AkkaHttpServerMetrics(instrumentFactory)
}
