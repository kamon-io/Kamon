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

import com.linecorp.armeria.common.{HttpRequest, HttpResponse, HttpStatus, ResponseHeaders}
import com.linecorp.armeria.server.annotation.{Blocking, Get, Param}
import kamon.Kamon
import org.slf4j.LoggerFactory

final class UserRoutes {
  val log = LoggerFactory.getLogger(this.getClass)

  @Get("/users")
  def getUsers(req: HttpRequest): HttpResponse = {
    log.info(s"${Kamon.currentContext().hashCode()} - Looking for users ...")
    val responseHeaders = ResponseHeaders.builder(HttpStatus.OK).add(req.headers()).build()
    HttpResponse.of(responseHeaders)
  }

  @Get("/users/{userId}/accounts/{accountId}")
  def getUserAccount(@Param("userId") userId: String, @Param("accountId") accountId: String): HttpResponse = {
    log.info(s"${Kamon.currentContext().hashCode()} - Looking for user $userId account $accountId ...")
    HttpResponse.of(HttpStatus.OK)
  }

  @Get("/users/{userId}")
  def getUser(@Param("userId") userId: String, req: HttpRequest): HttpResponse = {
    log.info(s"${Kamon.currentContext().hashCode()} - Looking for user $userId ...")
    userId match {
      case "error" =>
        val responseHeaders = ResponseHeaders.builder(HttpStatus.INTERNAL_SERVER_ERROR).add(req.headers()).build()
        HttpResponse.of(responseHeaders)

      case "not-found" =>
        val responseHeaders = ResponseHeaders.builder(HttpStatus.NOT_FOUND).add(req.headers()).build()
        HttpResponse.of(responseHeaders)

      case _ =>
        val responseHeaders = ResponseHeaders.builder(HttpStatus.OK).add(req.headers()).build()
        HttpResponse.of(responseHeaders)
    }
  }

  @Blocking
  @Get("/users-blocking")
  def getUsersBlocking(req: HttpRequest): HttpResponse = {
    log.info(s"${Kamon.currentContext().hashCode()} - Looking for users under a blocking pool ...")
    val responseHeaders = ResponseHeaders.builder(HttpStatus.OK).add(req.headers()).build()
    HttpResponse.of(responseHeaders)
  }
}

object UserRoutes {
  def apply(): UserRoutes = new UserRoutes()
}

object Endpoints {
  val usersEndpoint = "users"
  val usersBlockingEndpoint = "users-blocking"
  val userAccountEndpoint = "users/ABC123/accounts/BCA213"
  val pathNotFoundEndpoint = "not/found"
  val docsEndpoint = "docs"
}
