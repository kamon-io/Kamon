/* =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
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

package kamon.armeria.instrumentation.client.timing

import java.util.concurrent.TimeUnit

import com.linecorp.armeria.common.logging.RequestLog
import kamon.Kamon
import kamon.trace.Span

/**
  * Based on Armeria Brave Client implementation
  * https://github.com/line/armeria/blob/master/brave/src/main/java/com/linecorp/armeria/client/brave/BraveClient.java
  */
object Timing {

  def takeTimings(log: RequestLog, span: Span): Unit = {
    Option(log.connectionTimings()).foreach(timings => {

      logTiming(span, "connection-acquire.start", "connection-acquire.end",
        timings.connectionAcquisitionStartTimeMicros(),
        timings.connectionAcquisitionDurationNanos())

      if (timings.dnsResolutionDurationNanos() != -1) {
        logTiming(span, "dns-resolve.start", "dns-resolve.end",
          timings.dnsResolutionStartTimeMicros(),
          timings.dnsResolutionDurationNanos())
      }

      if (timings.socketConnectDurationNanos() != -1) {
        logTiming(span, "socket-connect.start", "socket-connect.end",
          timings.socketConnectStartTimeMicros(),
          timings.socketConnectDurationNanos())
      }

      if (timings.pendingAcquisitionDurationNanos() != -1) {
        logTiming(span, "connection-reuse.start", "connection-reuse.end",
          timings.pendingAcquisitionStartTimeMicros(),
          timings.pendingAcquisitionDurationNanos())
      }
    })
  }

  private def logTiming(span: Span,
                        startName: String,
                        endName: String,
                        startTimeMicros: Long,
                        durationNanos: Long): Unit = {

    val startTimeNanos = TimeUnit.NANOSECONDS.convert(startTimeMicros, TimeUnit.MICROSECONDS)

    span.mark(startName, Kamon.clock().toInstant(startTimeNanos))
    span.mark(endName, Kamon.clock().toInstant(startTimeNanos + durationNanos))
  }

}
