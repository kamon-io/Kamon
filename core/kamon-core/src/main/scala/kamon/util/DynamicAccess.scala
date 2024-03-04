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

import scala.collection.immutable
import java.lang.reflect.InvocationTargetException
import scala.reflect.ClassTag

/**
  * Utility class for creating instances from a FQCN, see [1] for the original source. It uses reflection to turn
  * fully-qualified class names into a `Class[_]` from which instances can be created. All methods in this class are
  * unsafe and might throw exceptions that should be handled by users if this class.
  *
  * [1] https://github.com/akka/akka/blob/master/akka-actor/src/main/scala/akka/actor/ReflectiveDynamicAccess.scala
  */
class DynamicAccess(val classLoader: ClassLoader) {

  /**
    * Tries to load a class that matches the provided fully-qualified class name.
    */
  def getClassFor[T: ClassTag](fqcn: String): Class[_ <: T] = {
    val c = Class.forName(fqcn, false, classLoader).asInstanceOf[Class[_ <: T]]
    val t = implicitly[ClassTag[T]].runtimeClass
    if (t.isAssignableFrom(c)) c else throw new ClassCastException(s"$t is not assignable from $c")
  }

  /**
    * Tries to create an instance of the provided class, passing the provided arguments to the constructor.
    */
  def createInstanceFor[T: ClassTag](clazz: Class[_], args: immutable.Seq[(Class[_], AnyRef)]): T =
    try {
      val types = args.map(_._1).toArray
      val values = args.map(_._2).toArray
      val constructor = clazz.getDeclaredConstructor(types: _*)
      constructor.setAccessible(true)
      val obj = constructor.newInstance(values: _*)
      val t = implicitly[ClassTag[T]].runtimeClass
      if (t.isInstance(obj)) obj.asInstanceOf[T]
      else throw new ClassCastException(clazz.getName + " is not a subtype of " + t)
    } catch { case i: InvocationTargetException if i.getTargetException ne null => throw i.getTargetException }

  /**
    * Tries to create an instance of the provided fully-qualified class name, passing the provided arguments to the
    * constructor.
    */
  def createInstanceFor[T: ClassTag](fqcn: String, args: immutable.Seq[(Class[_], AnyRef)]): T =
    createInstanceFor(getClassFor(fqcn), args)

}
