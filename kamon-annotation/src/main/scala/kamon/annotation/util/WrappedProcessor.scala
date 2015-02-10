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

package kamon.annotation.util

import javax.el.ELProcessor
import scala.collection.mutable

class WrappedProcessor(val processor: ELProcessor) extends AnyVal {
  import scala.collection.JavaConverters._
  import WrappedProcessor._

  def evalToString(expression: String): String = sanitize(expression) map (processor.eval(_).toString) getOrElse expression

  def evalToMap(expression: String): mutable.Map[String, String] = {
    sanitize(expression) map (processor.eval(_).asInstanceOf[java.util.Map[String, String]].asScala) getOrElse mutable.Map.empty
  }
}

object WrappedProcessor {
  val Pattern = """[#|$]\{(.*)\}""".r

  def sanitize(expression: String): Option[String] = expression match {
    case Pattern(ex) ⇒ Some(ex)
    case _           ⇒ None
  }
}