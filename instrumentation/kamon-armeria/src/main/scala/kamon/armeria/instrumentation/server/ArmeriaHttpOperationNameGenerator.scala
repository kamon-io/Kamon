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

package kamon.armeria.instrumentation.server

import java.util.Locale

import kamon.armeria.instrumentation.BaseArmeriaHttpOperationNameGenerator
import kamon.instrumentation.http.HttpMessage.Request
/**
 * A GET request to https://localhost:8080/kamon-io/Kamon will generate the following operationName
 * kamon-io.Kamon.get
 */
class ArmeriaHttpOperationNameGenerator extends BaseArmeriaHttpOperationNameGenerator {

  override protected def name(request: Request, normalisedPath: String): String =
    s"$normalisedPath${request.method.toLowerCase(Locale.ENGLISH)}"

  override protected def key(request: Request): String =
    s"${request.method}${request.path}"
}

object ArmeriaHttpOperationNameGenerator {
  def apply() = new ArmeriaHttpOperationNameGenerator()
}


