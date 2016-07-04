/* =========================================================================================
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

package kamon.util.logger

import org.slf4j.{ Logger ⇒ SLF4JLogger }

class LazyLogger(val logger: SLF4JLogger) {

  @inline final def isTraceEnabled = logger.isTraceEnabled
  @inline final def trace(msg: ⇒ String): Unit = if (isTraceEnabled) logger.trace(msg.toString)
  @inline final def trace(msg: ⇒ String, t: ⇒ Throwable): Unit = if (isTraceEnabled) logger.trace(msg, t)

  @inline final def isDebugEnabled = logger.isDebugEnabled
  @inline final def debug(msg: ⇒ String): Unit = if (isDebugEnabled) logger.debug(msg.toString)
  @inline final def debug(msg: ⇒ String, t: ⇒ Throwable): Unit = if (isDebugEnabled) logger.debug(msg, t)

  @inline final def isErrorEnabled = logger.isErrorEnabled
  @inline final def error(msg: ⇒ String): Unit = if (isErrorEnabled) logger.error(msg.toString)
  @inline final def error(msg: ⇒ String, t: ⇒ Throwable): Unit = if (isErrorEnabled) logger.error(msg, t)

  @inline final def isInfoEnabled = logger.isInfoEnabled
  @inline final def info(msg: ⇒ String): Unit = if (isInfoEnabled) logger.info(msg.toString)
  @inline final def info(msg: ⇒ String, t: ⇒ Throwable): Unit = if (isInfoEnabled) logger.info(msg, t)

  @inline final def isWarnEnabled = logger.isWarnEnabled
  @inline final def warn(msg: ⇒ String): Unit = if (isWarnEnabled) logger.warn(msg.toString)
  @inline final def warn(msg: ⇒ String, t: ⇒ Throwable): Unit = if (isWarnEnabled) logger.warn(msg, t)
}

object LazyLogger {
  import scala.reflect.{ classTag, ClassTag }

  def apply(name: String): LazyLogger = new LazyLogger(org.slf4j.LoggerFactory.getLogger(name))
  def apply(cls: Class[_]): LazyLogger = apply(cls.getName)
  def apply[C: ClassTag](): LazyLogger = apply(classTag[C].runtimeClass.getName)
}