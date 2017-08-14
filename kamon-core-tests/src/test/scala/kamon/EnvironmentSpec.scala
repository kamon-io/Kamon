package kamon

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

class EnvironmentSpec extends WordSpec with Matchers {
  private val baseConfig = ConfigFactory.parseString(
    """
      |kamon.environment {
      |  service = environment-spec
      |  host = auto
      |  instance = auto
      |}
    """.stripMargin
  )

  "the Kamon environment" should {
    "assign a host and instance name when they are set to 'auto'" in {
      val env = Environment.fromConfig(baseConfig)

      env.host shouldNot be("auto")
      env.instance shouldNot be("auto")
      env.instance shouldBe s"environment-spec@${env.host}"
    }

    "use the configured host and instance, if provided" in {
      val customConfig = ConfigFactory.parseString(
        """
          |kamon.environment {
          |  host = spec-host
          |  instance = spec-instance
          |}
        """.stripMargin)

      val env = Environment.fromConfig(customConfig.withFallback(baseConfig))

      env.host should be("spec-host")
      env.instance should be("spec-instance")
    }

    "always return the same incarnation name" in {
      val envOne = Environment.fromConfig(baseConfig)
      val envTwo = Environment.fromConfig(baseConfig)

      envOne.incarnation shouldBe envTwo.incarnation
    }
  }
}
