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

package kamon
package util

import org.slf4j.LoggerFactory

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext

/**
  * Execution Context that runes any submitted task on the calling thread. This is meant to be used for small code
  * blocks like recording or finishing a Span that usually happen after completing a Future.
  */
object CallingThreadExecutionContext extends ExecutionContext with Executor {

  private val _logger = LoggerFactory.getLogger("kamon.util.CallingThreadExecutionContext")

  override def execute(runnable: Runnable): Unit =
    runnable.run

  override def reportFailure(t: Throwable): Unit =
    _logger.error(t.getMessage, t)
}
