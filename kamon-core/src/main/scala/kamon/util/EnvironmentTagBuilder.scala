package kamon.util

import com.typesafe.config.Config
import kamon.{Environment, Kamon}

import scala.collection.JavaConverters._


/**
  *   Utility class to create a Map[String, String] encoding all the Environment information based on the provided
  *   Config. The Config instance is expected to have the following members:
  *
  *     - service:          Boolean. If true a service tag is included.
  *     - host:             Boolean. If true a host tag is included.
  *     - instance:         Boolean. If true a instance tag is included.
  *     - blacklisted-tags: List[String]. List of Environment tags that should not be included in the result.
  *
  *   This utility is meant to be used mostly by reporter modules.
  *
  */
object EnvironmentTagBuilder {

  def create(config: Config): Map[String, String] =
    create(Kamon.environment, config)

  def create(env: Environment, config: Config): Map[String, String] = {
    val envTags = Map.newBuilder[String, String]

    if(config.getBoolean("service"))
      envTags += ("service" -> env.service)

    if(config.getBoolean("host"))
      envTags += ("host" -> env.host)

    if(config.getBoolean("instance"))
      envTags += ("instance" -> env.instance)

    val blacklistedTags = config.getStringList("blacklisted-tags").asScala
    env.tags.filterKeys(k => !blacklistedTags.contains(k)).foreach(p => envTags += p)

    envTags.result()
  }
}
