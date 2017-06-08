/* =========================================================================================
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


package kamon

//import uk.org.lidalia.slf4jext.Level
//import uk.org.lidalia.slf4jtest.{LoggingEvent, TestLogger}
//
//trait LogInterceptor {
//
//  def interceptLog[T](level: Level)(code: => T)(implicit tl: TestLogger): Seq[LoggingEvent] = {
//    import scala.collection.JavaConverters._
//    tl.clear()
//    val run = code
//    tl.getLoggingEvents().asScala.filter(_.getLevel == level)
//  }
//}
