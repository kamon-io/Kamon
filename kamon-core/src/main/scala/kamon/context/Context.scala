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

package kamon.context

class Context private (private[context] val entries: Map[Key[_], Any]) {
  def get[T](key: Key[T]): T =
    entries.getOrElse(key, key.emptyValue).asInstanceOf[T]

  def withKey[T](key: Key[T], value: T): Context =
    new Context(entries.updated(key, value))
}

object Context {
  val Empty = new Context(Map.empty)

  def apply(): Context =
    Empty

  def create(): Context =
    Empty

  def apply[T](key: Key[T], value: T): Context =
    new Context(Map(key -> value))

  def create[T](key: Key[T], value: T): Context =
    apply(key, value)
}


trait Key[T] {
  def name: String
  def emptyValue: T
  def broadcast: Boolean
}

object Key {

  def local[T](name: String, emptyValue: T): Key[T] =
    new Default[T](name, emptyValue, false)

  def broadcast[T](name: String, emptyValue: T): Key[T] =
    new Default[T](name, emptyValue, true)


  private class Default[T](val name: String, val emptyValue: T, val broadcast: Boolean) extends Key[T] {
    override def hashCode(): Int =
      name.hashCode

    override def equals(that: Any): Boolean =
      that.isInstanceOf[Default[_]] && that.asInstanceOf[Default[_]].name == this.name
  }
}