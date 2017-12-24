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

package kamon.akka.http

import kamon.Kamon


object AkkaHttpMetrics {

  /**
    *  Tracks the number of requests currently executing on the Akka HTTP Server. Metrics will be refined with tags:
    *    - interface: Listening Interface
    *    - port: Listening Port
    */
  val ActiveRequests = Kamon.rangeSampler("akka.http.server.active-requests")

  /**
    *  Tracks the number of open connections on the Akka HTTP Server. Metrics will be refined with tags:
    *    - interface: Listening interface
    *    - port: Listening port
    */
  val OpenConnections = Kamon.rangeSampler("akka.http.server.open-connections")
}
