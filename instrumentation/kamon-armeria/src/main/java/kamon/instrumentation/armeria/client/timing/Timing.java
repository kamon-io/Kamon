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

package kamon.instrumentation.armeria.client.timing;

import com.linecorp.armeria.common.logging.ClientConnectionTimings;
import com.linecorp.armeria.common.logging.RequestLog;
import kamon.Kamon;
import kamon.trace.Span;

import java.util.concurrent.TimeUnit;

/**
 * Based on Armeria
 * <a href="https://github.com/line/armeria/blob/master/brave/src/main/java/com/linecorp/armeria/client/brave/BraveClient.java">BraveClient</a>
 * implementation.
 */
public final class Timing {

  public static void takeTimings(RequestLog log, Span span) {
    ClientConnectionTimings timings = log.connectionTimings();
    if (timings != null) {

      logTiming(span, "connection-acquire.start", "connection-acquire.end",
          timings.connectionAcquisitionStartTimeMicros(),
          timings.connectionAcquisitionDurationNanos());

      if (timings.dnsResolutionDurationNanos() != -1) {
        logTiming(span, "dns-resolve.start", "dns-resolve.end",
            timings.dnsResolutionStartTimeMicros(),
            timings.dnsResolutionDurationNanos());
      }

      if (timings.socketConnectDurationNanos() != -1) {
        logTiming(span, "socket-connect.start", "socket-connect.end",
            timings.socketConnectStartTimeMicros(),
            timings.socketConnectDurationNanos());
      }

      if (timings.pendingAcquisitionDurationNanos() != -1) {
        logTiming(span, "connection-reuse.start", "connection-reuse.end",
            timings.pendingAcquisitionStartTimeMicros(),
            timings.pendingAcquisitionDurationNanos());
      }
    }
  }

  private static void logTiming(Span span,
                                String startName,
                                String endName,
                                long startTimeMicros,
                                long durationNanos) {

    final long startTimeNanos = TimeUnit.NANOSECONDS.convert(startTimeMicros, TimeUnit.MICROSECONDS);

    span.mark(startName, Kamon.clock().toInstant(startTimeNanos));
    span.mark(endName, Kamon.clock().toInstant(startTimeNanos + durationNanos));
  }


}
