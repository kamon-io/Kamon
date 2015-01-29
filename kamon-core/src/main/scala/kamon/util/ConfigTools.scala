package kamon.util

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

object ConfigTools {
  implicit class Syntax(val config: Config) extends AnyVal {
    // We are using the deprecated .getNanoseconds option to keep Kamon source code compatible with
    // versions of Akka using older typesafe-config versions.

    def getFiniteDuration(path: String): FiniteDuration =
      FiniteDuration(config.getNanoseconds(path), TimeUnit.NANOSECONDS)

    def firstLevelKeys: Set[String] = {
      import scala.collection.JavaConverters._

      config.entrySet().asScala.map {
        case entry ⇒ entry.getKey.takeWhile(_ != '.')
      } toSet
    }
  }

}
