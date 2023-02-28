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

package kamon.testkit

import kamon.instrumentation.http.{HttpMessage, HttpOperationNameGenerator}

class TestNameGenerator extends HttpOperationNameGenerator {



//  def serverOperationName(request: HttpRequest): String = {
//    val path = request.uri.path.toString()
//    // turns "/dummy-path" into "dummy"
//    path.substring(1).split("-")(0)
//  }
//
//  def clientOperationName(request: HttpRequest): String = "client " + request.uri.path.toString()
  override def name(request: HttpMessage.Request): Option[String] = Some("test")
}
