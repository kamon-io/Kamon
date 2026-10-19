/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

package kamon.instrumentation.finagle.client

import com.twitter.finagle.context.Contexts
import kamon.instrumentation.finagle.client.FinagleHttpInstrumentation.KamonRequestHandler

private[finagle] object BroadcastRequestHandler {
  private val RequestHandlerKey: Contexts.local.Key[KamonRequestHandler] = new Contexts.local.Key[KamonRequestHandler]

  def get: Option[KamonRequestHandler] = Contexts.local.get(RequestHandlerKey)

  def let[R](requestHandler: KamonRequestHandler)(f: => R): R =
    Contexts.local.let(RequestHandlerKey, requestHandler)(f)
}
