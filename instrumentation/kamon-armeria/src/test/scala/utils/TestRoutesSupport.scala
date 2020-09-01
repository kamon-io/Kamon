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
import com.linecorp.armeria.server.annotation.{Get, Param}

final class TestRoutesSupport {
  @Get("/dummy")
  def getDummy(req: HttpRequest): HttpResponse = {
    val responseHeaders = ResponseHeaders.builder(HttpStatus.OK).add(req.headers()).build()
    HttpResponse.of(responseHeaders)
  }

  @Get("/dummy-resources/{resource}/other-resources/{other}")
  def getResource(@Param("resource") resource: String, @Param("other") other: String): HttpResponse = {
    println(s"Received a request to retrieve resource $resource and $other")
    HttpResponse.of(HttpStatus.OK)
  }

  @Get("/dummy-error")
  def getDummyError(req: HttpRequest): HttpResponse = {
    val responseHeaders = ResponseHeaders.builder(HttpStatus.INTERNAL_SERVER_ERROR).add(req.headers()).build()
    HttpResponse.of(responseHeaders)
  }

  @Get("/dummy-resource-not-found")
  def getDummyResourceNotFound(req: HttpRequest): HttpResponse = {
    val responseHeaders = ResponseHeaders.builder(HttpStatus.NOT_FOUND).add(req.headers()).build()
    HttpResponse.of(responseHeaders)
  }

}

object TestRoutesSupport {
  def apply(): TestRoutesSupport = new TestRoutesSupport()
}

object TestEndpoints {
  val dummyPath = "dummy"
  val dummyErrorPath = "dummy-error"
  val dummyMultipleResourcesPath = "dummy-resources/a/other-resources/b"
  val dummyNotFoundPath = "dummy-not-found"
  val dummyResourceNotFoundPath = "dummy-resource-not-found"
}
