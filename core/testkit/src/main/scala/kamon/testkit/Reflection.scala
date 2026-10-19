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

package kamon

import scala.reflect.ClassTag

object Reflection {

  /**
    * Retrieves a member from the target object using reflection.
    */
  def getField[T, R](target: T, fieldName: String)(implicit classTag: ClassTag[T]): R = {
    val field = classTag.runtimeClass.getDeclaredFields.find(_.getName.contains(fieldName)).get
    field.setAccessible(true)
    field.get(target).asInstanceOf[R]
  }

  /**
    * Retrieves a member from the target object using reflection.
    */
  def getFieldFromClass[T](target: Any, className: String, fieldName: String): T = {
    val field = Class.forName(className).getDeclaredFields.find(_.getName.contains(fieldName)).get
    field.setAccessible(true)
    field.get(target).asInstanceOf[T]
  }

  /**
    * Invokes a method on the target object using reflection.
    */
  def invoke[T, R](target: Any, fieldName: String, parameters: (Class[_], AnyRef)*)(implicit
    classTag: ClassTag[T]
  ): R = {
    val parameterClasses = parameters.map(_._1)
    val parameterInstances = parameters.map(_._2)
    val method = classTag.runtimeClass.getDeclaredMethod(fieldName, parameterClasses: _*)
    method.setAccessible(true)
    method.invoke(target, parameterInstances: _*).asInstanceOf[R]
  }
}
