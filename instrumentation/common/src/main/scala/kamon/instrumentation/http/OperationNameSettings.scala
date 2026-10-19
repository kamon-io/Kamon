/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.instrumentation.http

import kamon.util.Filter
import org.slf4j.LoggerFactory

import scala.util.Try

final case class OperationNameSettings(
  defaultOperationName: String,
  operationMappings: Map[Filter.Glob, String],
  operationNameGenerator: HttpOperationNameGenerator
) {

  private val logger = LoggerFactory.getLogger(classOf[OperationNameSettings])

  private[http] def operationName(request: HttpMessage.Request): String = {
    Try {
      val requestPath = request.path

      // first apply any mappings rules
      val customMapping = operationMappings.collectFirst {
        case (pattern, operationName) if pattern.accept(requestPath) => operationName
      } orElse {
        // fallback to use any configured name generator
        operationNameGenerator.name(request)
      }

      customMapping.getOrElse(defaultOperationName)
    } getOrElse (defaultOperationName)
  }
}
