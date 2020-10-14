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

package utils

import java.net.InetSocketAddress

import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.docs.DocService
import com.linecorp.armeria.server.healthcheck.HealthCheckService

object ArmeriaServerSupport {

  def startArmeriaServer(port: Int, https: Boolean = false): Server = {
    val server = Server
      .builder()
      .service("/health-check", HealthCheckService.of())
      .serviceUnder("/docs", new DocService())
      .annotatedService().build(TestRoutesSupport())
      .http(InetSocketAddress.createUnresolved("localhost", port))
      .build()

    server
      .start()
      .join()

    server
  }

}
