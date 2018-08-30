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
package context

import java.util.{Map => JavaMap}
import scala.collection.JavaConverters._

class Context private (private[context] val entries: Map[Context.Key[_], Any], private[context] val tags: Map[String, String]) {

  def get[T](key: Context.Key[T]): T =
    entries.getOrElse(key, key.emptyValue).asInstanceOf[T]

  def getTag(tagKey: String): Option[String] =
    tags.get(tagKey)

  def withKey[T](key: Context.Key[T], value: T): Context =
    new Context(entries.updated(key, value), tags)

  def withTag(tagKey: String, tagValue: String): Context =
    new Context(entries, tags.updated(tagKey, tagValue))

  def withTags(tags: Map[String, String]): Context =
    new Context(entries, this.tags ++ tags)

  def withTags(tags: JavaMap[String, String]): Context =
    new Context(entries, this.tags ++ tags.asScala.toMap)


}

object Context {

  val Empty = new Context(Map.empty, Map.empty)

  def of(tags: JavaMap[String, String]): Context =
    new Context(Map.empty, tags.asScala.toMap)

  def of(tags: Map[String, String]): Context =
    new Context(Map.empty, tags)

  def of[T](key: Context.Key[T], value: T): Context =
    new Context(Map(key -> value), Map.empty)

  def of[T](key: Context.Key[T], value: T, tags: JavaMap[String, String]): Context =
    new Context(Map(key -> value), tags.asScala.toMap)

  def of[T](key: Context.Key[T], value: T, tags: Map[String, String]): Context =
    new Context(Map(key -> value), tags)

  def of[T, U](keyOne: Context.Key[T], valueOne: T, keyTwo: Context.Key[U], valueTwo: U): Context =
    new Context(Map(keyOne -> valueOne, keyTwo -> valueTwo), Map.empty)

  def of[T, U](keyOne: Context.Key[T], valueOne: T, keyTwo: Context.Key[U], valueTwo: U, tags: JavaMap[String, String]): Context =
    new Context(Map(keyOne -> valueOne, keyTwo -> valueTwo), tags.asScala.toMap)

  def of[T, U](keyOne: Context.Key[T], valueOne: T, keyTwo: Context.Key[U], valueTwo: U, tags: Map[String, String]): Context =
    new Context(Map(keyOne -> valueOne, keyTwo -> valueTwo), tags)

  def key[T](name: String, emptyValue: T): Context.Key[T] =
    new Context.Key(name, emptyValue)

  /**
    * Encapsulates the type, name and empty value for a context entry. All reads and writes from a context instance
    * must be done using a context key, which will ensure the right type is used on both operations. The key's name
    * is used when configuring mappings and incoming/outgoing/returning codecs for context propagation across channels.
    *
    * If you try to read an entry from a context and such entry is not present, the empty value for the key is returned
    * instead.
    *
    * @param name Key name. Must be unique.
    * @param emptyValue Value to be returned when reading from a context that doesn't have an entry with this key.
    * @tparam ValueType Type of the value to be held on the context with this key.
    */
  final class Key[ValueType](val name: String, val emptyValue: ValueType) {
    override def hashCode(): Int =
      name.hashCode

    override def equals(that: Any): Boolean =
      that.isInstanceOf[Context.Key[_]] && that.asInstanceOf[Context.Key[_]].name == this.name
  }
}