/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.netty.util

import io.netty.util.concurrent.EventExecutor
import kamon.netty.instrumentation.mixin.NamedEventLoopGroup

object EventLoopUtils {
  def name(eventLoop: EventExecutor): String = {
    val sanitize:String => String = str => str.replaceAll("(.)(\\p{Upper})", "$1-$2").toLowerCase()
    s"${eventLoop.parent().asInstanceOf[NamedEventLoopGroup].getName}-${sanitize(eventLoop.getClass.getSimpleName)}"
  }
}
