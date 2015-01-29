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
class Entity(val name: String, val category: String, val metadata: Map[String, String]) {

  override def equals(o: Any): Boolean = {
    if (this eq o.asInstanceOf[AnyRef])
      true
    else if ((o.asInstanceOf[AnyRef] eq null) || !o.isInstanceOf[Entity])
      false
    else {
      val thatAsEntity = o.asInstanceOf[Entity]
      category == thatAsEntity.category && name == thatAsEntity.name
    }
  }

  override def hashCode: Int = {
    var result: Int = name.hashCode
    result = 31 * result + category.hashCode
    return result
  }
}

object Entity {
  def apply(name: String, category: String): Entity =
    apply(name, category, Map.empty)

  def apply(name: String, category: String, metadata: Map[String, String]): Entity =
    new Entity(name, category, metadata)
}