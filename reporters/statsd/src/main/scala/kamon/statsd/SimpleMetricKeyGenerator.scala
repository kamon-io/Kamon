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

package kamon.statsd

import com.typesafe.config.Config
import kamon.Kamon
import kamon.tag.{Tag, TagSet}

trait MetricKeyGenerator {
  def generateKey(name: String, tags: TagSet): String
}

class SimpleMetricKeyGenerator(statsDConfig: Config) extends MetricKeyGenerator {
  type Normalizer = String => String

  val configSettings: Config = statsDConfig.getConfig("simple-metric-key-generator")
  val serviceName: String = Kamon.environment.service
  val includeHostname: Boolean = configSettings.getBoolean("include-hostname")
  val hostname: String = Kamon.environment.host
  val includeEnvironmentTags: Boolean = configSettings.getBoolean("include-environment-tags")
  val environmentTags: TagSet = Kamon.environment.tags
  val normalizer: Normalizer = createNormalizer(configSettings.getString("metric-name-normalization-strategy"))
  val normalizedHostname: String = normalizer(hostname)

  val baseName: String =
    if (includeHostname) s"$serviceName.$normalizedHostname"
    else serviceName

  private def createNormalizer(strategy: String): Normalizer = strategy match {
    case "percent-encode" => PercentEncoder.encode
    case "normalize" =>
      (s: String) => s.replace(": ", "-").replace(":", "-").replace(" ", "_").replace("/", "_").replace(".", "_")
  }

  def generateKey(name: String, metricTags: TagSet): String = {
    val tags = if (includeEnvironmentTags) metricTags.withTags(environmentTags) else metricTags
    val stringTags = if (tags.nonEmpty) "." + sortAndConcatenateTags(tags) else ""
    s"$baseName.${normalizer(name)}$stringTags"
  }

  private def sortAndConcatenateTags(tags: TagSet): String = {
    tags
      .all()
      .sortBy(tag => (tag.key, Tag.unwrapValue(tag).toString))
      .flatMap { tag => List(tag.key, Tag.unwrapValue(tag).toString) }
      .map(normalizer)
      .mkString(".")
  }
}

object PercentEncoder {

  def encode(originalString: String): String = {
    val encodedString = new StringBuilder()

    for (character <- originalString) {
      if (shouldEncode(character)) {
        encodedString.append('%')
        val charHexValue = Integer.toHexString(character).toUpperCase
        if (charHexValue.length < 2)
          encodedString.append('0')

        encodedString.append(charHexValue)

      } else {
        encodedString.append(character)
      }
    }
    encodedString.toString()
  }

  private def shouldEncode(ch: Char): Boolean = {
    if (ch > 128 || ch < 0) true
    else " %$&+,./:;=?@<>#%".indexOf(ch) >= 0
  }
}
