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

package kamon.newrelic

import com.newrelic.telemetry.Attributes
import com.typesafe.config.Config
import kamon.status.Environment
import kamon.tag.{Tag, TagSet}

import scala.collection.JavaConverters._

object AttributeBuddy {
  def addTagsFromTagSets(tagSeq: Seq[TagSet], attributes: Attributes = new Attributes()): Attributes = {
    tagSeq.foreach { tagset: TagSet =>
      tagset.iterator().foreach(pair => {
        val value: Any = Tag.unwrapValue(pair)
        putTypedValue(attributes, pair.key, value)
      })
    }
    attributes
  }

  def addTagsFromConfig(config: Config, attributes: Attributes = new Attributes()): Attributes = {
    config.entrySet().asScala.foreach { entry =>
      val key: String = entry.getKey
      val v: Any = entry.getValue.unwrapped()
      putTypedValue(attributes, key, v)
    }
    attributes
  }

  private def putTypedValue(attributes: Attributes, key: String, value: Any) = {
    // Maintain the type of the tag value consistent with NR Attribute types
    value match {
      case v: String  => attributes.put(key, v)
      case v: Number  => attributes.put(key, v)
      case v: Boolean => attributes.put(key, v)
    }
  }

  def buildCommonAttributes(environment: Environment): Attributes = {
    val attributes = new Attributes()
      .put("instrumentation.provider", "kamon-agent")
      .put("service.name", environment.service)
      .put("host.hostname", environment.host)
    AttributeBuddy.addTagsFromTagSets(Seq(environment.tags), attributes)
    attributes
  }
}
