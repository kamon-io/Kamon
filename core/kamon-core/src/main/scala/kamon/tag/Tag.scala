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

package kamon.tag

import java.lang.{Boolean => JBoolean, Long => JLong, String => JString}

/**
  * Marker trait for allowed Tag implementations. Users are not meant to create implementations of this trait outside
  * of Kamon. Furthermore, users of TagSet might never need to interact with these classes but rather perform lookups
  * using the lookup DSL.
  */
sealed trait Tag {
  def key: JString
}

object Tag {

  trait Pair[T] {
    def key: JString
    def value: T
  }

  /**
    * Represents a String key pointing to a String value.
    */
  trait String extends Tag {
    def value: JString
  }

  /**
    * Represents a String key pointing to a Boolean value.
    */
  trait Boolean extends Tag {
    def value: JBoolean
  }

  /**
    * Represents a String key pointing to a Long value.
    */
  trait Long extends Tag {
    def value: JLong
  }

  /**
    * Returns the value held inside of a Tag instance. This utility function is specially useful when iterating over
    * tags but not caring about the concrete tag type.
    */
  def unwrapValue(tag: Tag): Any = tag match {
    case t: Tag.String  => t.value
    case t: Tag.Boolean => t.value
    case t: Tag.Long    => t.value
  }
}
