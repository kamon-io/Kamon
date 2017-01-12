/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

package kamon.akka.http.metrics

import akka.http.scaladsl.model.HttpResponse
import kamon.metric.instrument.InstrumentFactory
import kamon.metric.EntityRecorderFactoryCompanion
import kamon.util.http.HttpServerMetrics
import kamon.util.logger.LazyLogger

class AkkaHttpServerMetrics(instrumentFactory: InstrumentFactory) extends HttpServerMetrics(instrumentFactory) {
  val log = LazyLogger(classOf[AkkaHttpServerMetrics])

  val requestActive = minMaxCounter("request-active")
  val connectionOpen = minMaxCounter("connection-open")

  def recordRequest() = {
    log.debug("Logging a request")
    requestActive.increment()
  }

  def recordResponse(response: HttpResponse, traceName: String): Unit = {
    requestActive.decrement()
    super.recordResponse(traceName, response.status.intValue.toString)
  }

  def recordConnectionOpened(): Unit = connectionOpen.increment()
  def recordConnectionClosed(): Unit = connectionOpen.decrement()
}

object AkkaHttpServerMetrics extends EntityRecorderFactoryCompanion[AkkaHttpServerMetrics]("akka-http-server", new AkkaHttpServerMetrics(_))
