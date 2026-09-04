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

package kamon.instrumentation.logback.tools

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import kamon.Kamon
import kamon.tag.Lookups.coerce

class ContextTagConverter extends ClassicConverter {
  @volatile private var _tagName: String = null
  @volatile private var _default: String = null

  override def start(): Unit = {
    super.start()
    val firstOption = getFirstOption()

    if (firstOption != null && firstOption.nonEmpty) {
      val optionParts = firstOption.split(':')
      _tagName = optionParts(0)
      _default = if (optionParts.length > 1) optionParts(1) else ""
    }
  }

  override def convert(event: ILoggingEvent): String = {
    if (_tagName != null) {
      val context = Kamon.currentContext()
      context.tags.get(coerce(_tagName, _default))
    } else _default
  }
}
