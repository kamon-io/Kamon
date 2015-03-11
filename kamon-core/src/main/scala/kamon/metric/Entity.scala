/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.metric

/**
 *  Identify a `thing` that is being monitored by Kamon. A [[kamon.metric.Entity]] is used to identify tracked `things`
 *  in both the metrics recording and reporting sides. Only the name and category fields are used with determining
 *  equality between two entities.
 *
 *  // TODO: Find a better word for `thing`.
 */
case class Entity(name: String, category: String, tags: Map[String, String])

object Entity {
  def apply(name: String, category: String): Entity =
    apply(name, category, Map.empty)

  def create(name: String, category: String): Entity =
    apply(name, category, Map.empty)

  def create(name: String, category: String, tags: Map[String, String]): Entity =
    new Entity(name, category, tags)
}