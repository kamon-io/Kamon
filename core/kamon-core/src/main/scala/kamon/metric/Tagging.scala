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

package kamon.metric

import kamon.tag.TagSet

/**
  * Describes the tagging operations that can be performed on an metric or instrument instance. The tags on the
  * returned instrument are the result of combining any existent tags on the instrument with the newly provided tags,
  * overriding previous tags if needed.
  *
  * Since metrics do not have tags themselves (only instruments do), the returned instruments of tagging operations on
  * metrics only have the provided tags.
  */
trait Tagging[I] {

  /**
    * Returns an instrument with one additional tag defined by the provided key and value pair.
    */
  def withTag(key: String, value: String): I

  /**
    * Returns an instrument with one additional tag defined by the provided key and value pair.
    */
  def withTag(key: String, value: Boolean): I

  /**
    * Returns an instrument with one additional tag defined by the provided key and value pair.
    */
  def withTag(key: String, value: Long): I

  /**
    * Returns an instrument with additional tags from the provided TagSet.
    */
  def withTags(tags: TagSet): I
}
