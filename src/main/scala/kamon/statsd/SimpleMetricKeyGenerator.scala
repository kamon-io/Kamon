/*
 * =========================================================================================
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

package kamon.statsd

import com.typesafe.config.Config
import kamon.Kamon

import scala.collection.immutable.TreeMap

trait MetricKeyGenerator {
  def generateKey(name: String, tags: Map[String, String]): String
}

class SimpleMetricKeyGenerator(config: Config) extends MetricKeyGenerator {
  type Normalizer = String ⇒ String

  val configSettings = config.getConfig("kamon.statsd.simple-metric-key-generator")
  val application = Kamon.environment.service
  val includeHostname = configSettings.getBoolean("include-hostname")
  val hostname = Kamon.environment.host
  val normalizer = createNormalizer(configSettings.getString("metric-name-normalization-strategy"))
  val normalizedHostname = normalizer(hostname)

  val baseName: String =
    if (includeHostname) s"$application.$normalizedHostname"
    else application

  private def createNormalizer(strategy: String): Normalizer = strategy match {
    case "percent-encode" ⇒ PercentEncoder.encode
    case "normalize"      ⇒ (s: String) ⇒ s.replace(": ", "-").replace(":", "-").replace(" ", "_").replace("/", "_").replace(".", "_")
  }

  def generateKey(name: String, tags: Map[String, String]): String = {
    val stringTags = if (tags.nonEmpty) "." + sortAndConcatenateTags(tags) else ""
    s"$baseName.${normalizer(name)}$stringTags"
  }

  private def sortAndConcatenateTags(tags: Map[String, String]): String = {
    TreeMap(tags.toSeq:_ *)
      .flatMap {case (key, value)=> List(key, value)}
      .map(normalizer)
      .mkString(".")
  }

}

object PercentEncoder {

  def encode(originalString: String): String = {
    val encodedString = new StringBuilder()

    for (character ← originalString) {
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
