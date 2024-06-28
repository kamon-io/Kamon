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
package util

import com.typesafe.config.Config
import kamon.status.Environment
import kamon.tag.TagSet

import scala.collection.JavaConverters._

/**
  * Utility class for creating TagSet instances out of Environment instances. When an Environment is turned into tags
  * it will generate the following pairs:
  *
  *   - "service", with the service name from the Environment
  *   - "host", with the host name from the Environment
  *   - "instance", with the instance name from the Environment
  *   - One additional pair for each Environment tag, unless exclusions are provided when transforming.
  *
  * Most uses of this class are expected to happen in reporter modules, where the Environment information should usually
  * be exposed along with the metrics and spans.
  */
object EnvironmentTags {

  /**
    * Returns a TagSet instance with information from the provided Environment, using the provided config path to
    * retrieve the settings for the transformation from Kamon's Config. The configuration on the provided path is
    * expected to have the following structure:
    *
    * config {
    *   include-host = yes
    *   include-service = yes
    *   include-instance = yes
    *   exclude = [ ]
    * }
    *
    * If any of the settings are missing this function will default to include all Environment information.
    */
  def from(environment: Environment, path: String): TagSet =
    from(environment, Kamon.config().getConfig(path))

  /**
    * Returns a TagSet instance with information from the provided Environment, using the provided Config to read the
    * configuration settings for the transformation. The configuration is expected to have the following structure:
    *
    * config {
    *   include-host = yes
    *   include-service = yes
    *   include-instance = yes
    *   exclude = [ ]
    * }
    *
    * If any of the settings are missing this function will default to include all Environment information.
    */
  def from(environment: Environment, config: Config): TagSet = {
    val includeHost = if (config.hasPath("include-host")) config.getBoolean("include-host") else true
    val includeService = if (config.hasPath("include-service")) config.getBoolean("include-service") else true
    val includeInstance = if (config.hasPath("include-instance")) config.getBoolean("include-instance") else true
    val exclude = if (config.hasPath("exclude")) config.getStringList("exclude").asScala.toSet else Set.empty[String]

    from(environment, includeService, includeHost, includeInstance, exclude)
  }

  /**
    * Turns the information enclosed in the provided Environment instance into a TagSet.
    */
  def from(
    environment: Environment,
    includeService: Boolean,
    includeHost: Boolean,
    includeInstance: Boolean,
    exclude: Set[String]
  ): TagSet = {

    val tagSet = TagSet.builder()

    if (includeService)
      tagSet.add(TagKeys.Service, environment.service)

    if (includeHost)
      tagSet.add(TagKeys.Host, environment.host)

    if (includeInstance)
      tagSet.add(TagKeys.Instance, environment.instance)

    // We know for sure that all environment tags are treated as Strings
    environment.tags.iterator(_.toString).foreach { pair =>
      if (!exclude.contains(pair.key)) tagSet.add(pair.key, pair.value)
    }

    tagSet.build()
  }

  object TagKeys {
    val Host = "host"
    val Service = "service"
    val Instance = "instance"
  }
}
