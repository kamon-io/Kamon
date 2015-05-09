/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.util

import scala.concurrent.ExecutionContext
import org.slf4j.LoggerFactory

/**
 * For small code blocks that don't need to be run on a separate thread.
 */
object SameThreadExecutionContext extends ExecutionContext {
  val logger = LoggerFactory.getLogger("SameThreadExecutionContext")

  override def execute(runnable: Runnable): Unit = runnable.run
  override def reportFailure(t: Throwable): Unit = logger.error(t.getMessage, t)
}
