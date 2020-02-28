/*
 *  Copyright 2020 New Relic Corporation. All rights reserved.
 *  SPDX-License-Identifier: Apache-2.0
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
      case v: String => attributes.put(key, v)
      case v: Number => attributes.put(key, v)
      case v: Boolean => attributes.put(key, v)
    }
  }

  def buildCommonAttributes(environment: Environment): Attributes = {
    val attributes = new Attributes()
      .put("instrumentation.provider", "kamon-agent")
      .put("service.name", environment.service)
      .put("host", environment.host)
    AttributeBuddy.addTagsFromTagSets(Seq(environment.tags), attributes)
    attributes
  }
}
