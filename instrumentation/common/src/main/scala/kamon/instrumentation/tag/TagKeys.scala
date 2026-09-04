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

package kamon
package instrumentation
package tag

/**
  * Contains a collection of tag keys usually used through several instrumentation modules.
  */
object TagKeys {

  val Port = "port"
  val Interface = "interface"
  val Component = "component"
  val HttpStatusCode = "http.status_code"
  val HttpUrl = "http.url"
  val HttpMethod = "http.method"

}
