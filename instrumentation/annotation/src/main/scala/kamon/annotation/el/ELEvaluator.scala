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

package kamon.annotation.el

import java.util

import kamon.annotation.el.EnhancedELProcessor.Syntax

object StringEvaluator {
  def evaluate(obj: AnyRef)(str: String): String =
    ELProcessorFactory.withObject(obj).evalToString(str)

  def evaluate(clazz: Class[_])(str: String): String =
    ELProcessorFactory.withClass(clazz).evalToString(str)
}

object TagsEvaluator {
  def evaluate(obj: AnyRef)(str: String): Map[String, String] =
    ELProcessorFactory.withObject(obj).evalToMap(str)

  def eval(obj: AnyRef)(str: String): util.Map[String, String] = {
    import scala.collection.JavaConverters._
    evaluate(obj)(str).asJava
  }

  def eval(clazz: Class[_])(str: String): util.Map[String, String] = {
    import scala.collection.JavaConverters._
    evaluate(clazz)(str).asJava
  }

  def evaluate(clazz: Class[_])(str: String): Map[String, String] =
    ELProcessorFactory.withClass(clazz).evalToMap(str)
}
