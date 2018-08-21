/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

package kamon.jdbc.utils

import org.slf4j.{Logger, LoggerFactory}

trait LoggingSupport {

  protected val logger: Logger = LoggerFactory.getLogger(this.getClass)

  protected def logInfo(msg: => String): Unit = if (logger.isInfoEnabled) logger.info(msg)
  protected def logWarn(msg: => String): Unit = if (logger.isWarnEnabled) logger.warn(msg)
  protected def logError(msg: => String): Unit = if (logger.isErrorEnabled) logger.error(msg)
  protected def logError(msg: => String, exc: => Throwable): Unit = if (logger.isErrorEnabled) logger.error(msg, exc)
  protected def logDebug(msg: => String): Unit = if (logger.isDebugEnabled) logger.debug(msg)
  protected def logTrace(msg: => String): Unit = if (logger.isTraceEnabled) logger.trace(msg)
}
