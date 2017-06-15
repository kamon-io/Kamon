package kamon

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

class UtilsOnConfigSpec extends WordSpec with Matchers {
  val config = ConfigFactory.parseString(
    """
      | kamon.test {
      |   configuration-one {
      |     setting = value
      |     other-setting = other-value
      |   }
      |
      |   "config.two" {
      |     setting = value
      |   }
      | }
    """.stripMargin
  )

  "the utils on config syntax" should {
    "list all top level keys with a configuration" in {
      config.getConfig("kamon.test").topLevelKeys should contain only("configuration-one", "config.two")
    }

    "create a map from top level keys to the inner configuration objects"in {
      val extractedConfigurations = config.getConfig("kamon.test").configurations

      extractedConfigurations.keys should contain only("configuration-one", "config.two")
      extractedConfigurations("configuration-one").topLevelKeys should contain only("setting", "other-setting")
      extractedConfigurations("config.two").topLevelKeys should contain only("setting")
    }
  }

}
