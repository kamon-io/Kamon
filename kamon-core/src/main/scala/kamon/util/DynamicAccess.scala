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

package kamon.util

import scala.collection.immutable
import java.lang.reflect.InvocationTargetException
import scala.reflect.ClassTag
import scala.util.Try

/**
  * Utility class for creating instances from a FQCN, see [1] for the original source.
  *
  * It uses reflection to turn fully-qualified class names into `Class[_]` objects and creates instances from there
  * using `getDeclaredConstructor()` and invoking that.
  *
  * [1] https://github.com/akka/akka/blob/master/akka-actor/src/main/scala/akka/actor/ReflectiveDynamicAccess.scala
  */
class DynamicAccess(val classLoader: ClassLoader) {

  def getClassFor[T: ClassTag](fqcn: String): Try[Class[_ <: T]] =
    Try[Class[_ <: T]]({
      val c = Class.forName(fqcn, false, classLoader).asInstanceOf[Class[_ <: T]]
      val t = implicitly[ClassTag[T]].runtimeClass
      if (t.isAssignableFrom(c)) c else throw new ClassCastException(s"$t is not assignable from $c")
    })

  def createInstanceFor[T: ClassTag](clazz: Class[_], args: immutable.Seq[(Class[_], AnyRef)]): Try[T] =
    Try {
      val types = args.map(_._1).toArray
      val values = args.map(_._2).toArray
      val constructor = clazz.getDeclaredConstructor(types: _*)
      constructor.setAccessible(true)
      val obj = constructor.newInstance(values: _*)
      val t = implicitly[ClassTag[T]].runtimeClass
      if (t.isInstance(obj)) obj.asInstanceOf[T] else throw new ClassCastException(clazz.getName + " is not a subtype of " + t)
    } recover { case i: InvocationTargetException if i.getTargetException ne null ⇒ throw i.getTargetException }

  def createInstanceFor[T: ClassTag](fqcn: String, args: immutable.Seq[(Class[_], AnyRef)]): Try[T] =
    getClassFor(fqcn) flatMap { c ⇒ createInstanceFor(c, args) }

  def getObjectFor[T: ClassTag](fqcn: String): Try[T] = {
    val classTry =
      if (fqcn.endsWith("$")) getClassFor(fqcn)
      else getClassFor(fqcn + "$") recoverWith { case _ ⇒ getClassFor(fqcn) }
    classTry flatMap { c ⇒
      Try {
        val module = c.getDeclaredField("MODULE$")
        module.setAccessible(true)
        val t = implicitly[ClassTag[T]].runtimeClass
        module.get(null) match {
          case null                  ⇒ throw new NullPointerException
          case x if !t.isInstance(x) ⇒ throw new ClassCastException(fqcn + " is not a subtype of " + t)
          case x: T                  ⇒ x
        }
      } recover { case i: InvocationTargetException if i.getTargetException ne null ⇒ throw i.getTargetException }
    }
  }
}