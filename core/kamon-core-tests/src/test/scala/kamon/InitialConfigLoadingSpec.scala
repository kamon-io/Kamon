package kamon

import java.io._
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import kamon.metric.PeriodSnapshot
import kamon.trace.Span
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar._

import scala.util.{Failure, Success, Try}

class InitialConfigLoadingSpec extends WordSpec with Matchers with Eventually {

  "the initial config loading" should {
    "fallback to using reference configuration only when application.conf files are malformed" in {
      val process = Runtime.getRuntime.exec(createProcessWithConfig("kamon.KamonWithCustomConfig", "{This is a bad config}"))
      val processOutputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))

      eventually(timeout(10 seconds)) {
        val outputLine = processOutputReader.readLine()
        outputLine shouldBe "All Good"
      }

      if(process.isAlive) {
        process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
      }
    }
  }

  def createProcessWithConfig(mainClass: String, configContent: String): String = {
    val tempConfigFile = File.createTempFile("bad-config", ".conf")
    val writer = new BufferedWriter(new FileWriter(tempConfigFile))
    writer.write(configContent)
    writer.flush()
    writer.close()

    val configOptions = "-Dconfig.trace=loads -Dconfig.file=" + tempConfigFile.getAbsolutePath()
    System.getProperty("java.home") + File.separator + "bin" + File.separator + "java " + configOptions +
    " -cp " + System.getProperty("java.class.path") + " " + mainClass
  }
}

object KamonWithCustomConfig extends App {
  Try {
    Kamon.counter("test").withoutTags().increment()
  } match {
    case Success(_) => println("All Good")
    case Failure(_) => println("All Bad")
  }

}