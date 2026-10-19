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

import kamon.context.Context

/**
 * Allows for adding custom HTTP headers to the responses
 */
trait HttpServerResponseHeaderGenerator {

  /**
   * Returns the headers (name/value) to be appended to the response
   * @param context The context for the current request
   * @return
   */
  def headers(context: Context): Map[String, String]
}

/**
 * Default implementation of the ''HttpServerResponseHeaderGenerator''
 */
object DefaultHttpServerResponseHeaderGenerator extends HttpServerResponseHeaderGenerator {

  /**
   * Always returns empty Map
   */
  def headers(context: Context): Map[String, String] = Map.empty
}
