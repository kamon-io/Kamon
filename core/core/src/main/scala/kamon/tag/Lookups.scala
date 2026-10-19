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
package tag

import java.util.Optional
import kamon.tag.TagSet.Lookup

object Lookups {

  /**
    * Finds a value associated to the provided key and returns it. If the key is not present then a null is returned.
    */
  def any(key: String): Lookup[Any] = new Lookup[Any] {
    override def execute(storage: TagSet.Storage): Any =
      findAndTransform(key, storage, _any, null, _noSafetyCheck)
  }

  /**
    * Finds a String value associated to the provided key and returns it. If the key is not present or the value
    * associated with they is not a String then a null is returned.
    */
  def plain(key: String): Lookup[String] = new Lookup[String] {
    override def execute(storage: TagSet.Storage): String =
      findAndTransform(key, storage, _plainString, null, _stringSafetyCheck)
  }

  /**
    * Finds a String value associated to the provided key and returns it, wrapped in an Option[String]. If the key is
    * not present or the value associated with they is not a String then a None is returned.
    */
  def option(key: String): Lookup[Option[String]] = new Lookup[Option[String]] {
    override def execute(storage: TagSet.Storage): Option[String] =
      findAndTransform(key, storage, _stringOption, None, _stringSafetyCheck)
  }

  /**
    * Finds a String value associated to the provided key and returns it, wrapped in an Optional[String]. If the key
    * is not present or the value associated with they is not a String then Optional.empty() is returned.
    */
  def optional(key: String): Lookup[Optional[String]] = new Lookup[Optional[String]] {
    override def execute(storage: TagSet.Storage): Optional[String] =
      findAndTransform(key, storage, _stringOptional, Optional.empty(), _stringSafetyCheck)
  }

  /**
    * Finds the value associated to the provided key and coerces it to a String representation. If the key is not
    * present then "unknown" (as a String) will be returned. If the value associated with the key is not a String then
    * the value of the key will be transformed into a String and returned.
    *
    * This lookup type is guaranteed to return a non-null String representation of value.
    */
  def coerce(key: String): Lookup[String] = new Lookup[String] {
    override def execute(storage: TagSet.Storage): String = {
      val value = storage.get(key)
      if (value == null)
        "unknown"
      else
        value.toString
    }
  }

  /**
    * Finds the value associated to the provided key and coerces it to a String representation. If the key is not
    * present then the provided default will be returned. If the value associated with the key is not a String then
    * the value of the key will be transformed into a String and returned.
    *
    * This lookup type is guaranteed to return a non-null String representation of value.
    */
  def coerce(key: String, default: String): Lookup[String] = new Lookup[String] {
    override def execute(storage: TagSet.Storage): String = {
      val value = storage.get(key)
      if (value == null)
        default
      else
        value.toString
    }
  }

  /**
    * Finds a Boolean value associated to the provided key and returns it. If the key is not present or the value
    * associated with they is not a Boolean then a null is returned.
    */
  def plainBoolean(key: String): Lookup[java.lang.Boolean] = new Lookup[java.lang.Boolean] {
    override def execute(storage: TagSet.Storage): java.lang.Boolean =
      findAndTransform(key, storage, _plainBoolean, null, _booleanSafetyCheck)
  }

  /**
    * Finds a Boolean value associated to the provided key and returns it, wrapped in an Option[Boolean]. If the key
    * is not present or the value associated with they is not a Boolean then a None is returned.
    */
  def booleanOption(key: String): Lookup[Option[Boolean]] = new Lookup[Option[Boolean]] {
    override def execute(storage: TagSet.Storage): Option[Boolean] =
      findAndTransform(key, storage, _booleanOption, None, _booleanSafetyCheck)
  }

  /**
    * Finds a Boolean value associated to the provided key and returns it, wrapped in an Optional[Boolean]. If the key
    * is not present or the value associated with they is not a Boolean then Optional.empty() is returned.
    */
  def booleanOptional(key: String): Lookup[Optional[Boolean]] = new Lookup[Optional[Boolean]] {
    override def execute(storage: TagSet.Storage): Optional[Boolean] =
      findAndTransform(key, storage, _booleanOptional, Optional.empty(), _booleanSafetyCheck)
  }

  /**
    * Finds a Long value associated to the provided key and returns it. If the key is not present or the value
    * associated with they is not a Long then a null is returned.
    */
  def plainLong(key: String): Lookup[java.lang.Long] = new Lookup[java.lang.Long] {
    override def execute(storage: TagSet.Storage): java.lang.Long =
      findAndTransform(key, storage, _plainLong, null, _longSafetyCheck)
  }

  /**
    * Finds a Long value associated to the provided key and returns it, wrapped in an Option[Long]. If the key is
    * not present or the value associated with they is not a Long then a None is returned.
    */
  def longOption(key: String): Lookup[Option[Long]] = new Lookup[Option[Long]] {
    override def execute(storage: TagSet.Storage): Option[Long] =
      findAndTransform(key, storage, _longOption, None, _longSafetyCheck)
  }

  /**
    * Finds a Long value associated to the provided key and returns it, wrapped in an Optional[Long]. If the key
    * is not present or the value associated with they is not a Long then Optional.empty() is returned.
    */
  def longOptional(key: String): Lookup[Optional[Long]] = new Lookup[Optional[Long]] {
    override def execute(storage: TagSet.Storage): Optional[Long] =
      findAndTransform(key, storage, _longOptional, Optional.empty(), _longSafetyCheck)
  }

  ////////////////////////////////////////////////////////////////
  // Transformation helpers for the lookup DSL                  //
  ////////////////////////////////////////////////////////////////

  @inline
  private def findAndTransform[T, R](
    key: String,
    storage: TagSet.Storage,
    transform: R => T,
    default: T,
    safetyCheck: Any => Boolean
  ): T = {
    // This assumes that this code will only be used to lookup values from a Tags instance
    // for which the underlying map always has "null" as the default value.
    val value = storage.get(key)

    if (safetyCheck(value))
      transform(value.asInstanceOf[R])
    else
      default
  }

  private val _any = (a: Any) => a
  private val _noSafetyCheck = (a: Any) => a != null

  private val _plainString = (a: String) => a
  private val _stringOption = (a: String) => Option(a)
  private val _stringOptional = (a: String) => Optional.of(a)
  private val _stringSafetyCheck = (a: Any) => a.isInstanceOf[String]

  private val _plainLong = (a: java.lang.Long) => a
  private val _longOption = (a: Long) => Option(a)
  private val _longOptional = (a: Long) => Optional.of(a)
  private val _longSafetyCheck = (a: Any) => a.isInstanceOf[Long]

  private val _plainBoolean = (a: java.lang.Boolean) => a
  private val _booleanOption = (a: Boolean) => Option(a)
  private val _booleanOptional = (a: Boolean) => Optional.of(a)
  private val _booleanSafetyCheck = (a: Any) => a.isInstanceOf[Boolean]

}
