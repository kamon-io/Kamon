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
import com.linecorp.armeria.server.grpc.GrpcService
import com.linecorp.armeria.server.healthcheck.HealthCheckService

object ArmeriaServerSupport {

  def startArmeriaServer(port: Int, httpsPort: Option[Int] = None, grpcService: Option[GrpcService] = None): Server = {
    val serverBuilder = Server
      .builder()
      .service("/health-check", HealthCheckService.of())
      .serviceUnder("/docs", new DocService())
      .annotatedService().build(UserRoutes())
      .http(InetSocketAddress.createUnresolved("localhost", port))

    httpsPort.foreach {
      serverBuilder
        .https(_)
        .tlsSelfSigned()
    }

    grpcService.foreach {
      serverBuilder.service(_)
    }

    val server = serverBuilder.build()

    server
      .start()
      .join()

    server
  }

}
