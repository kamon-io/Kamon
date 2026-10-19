package kamon

import java.io.File
import java.util.concurrent.TimeUnit
import com.typesafe.config.{Config, ConfigFactory}
import kamon.metric.PeriodSnapshot
import kamon.trace.Span
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar._
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

class KamonLifecycleSpec extends AnyWordSpec with Matchers with Eventually {

  "the Kamon lifecycle" should {
    "keep the JVM running if modules are running" in {
      val process = Runtime.getRuntime.exec(createProcessCommand("kamon.KamonWithRunningReporter"))
      Thread.sleep(5000)
      process.isAlive shouldBe true
      process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
    }

    "let the JVM stop after all modules are stopped" in {
      val process = Runtime.getRuntime.exec(createProcessCommand("kamon.KamonWithTemporaryReporter"))
      Thread.sleep(2000)
      process.isAlive shouldBe true

      eventually(timeout(7 seconds)) {
        process.isAlive shouldBe false
        process.exitValue() shouldBe 0
      }
    }

    "not create any threads if Kamon.init was not called" in {
      val process = Runtime.getRuntime.exec(createProcessCommand("kamon.UsingKamonApisWithoutInit"))

      eventually(timeout(7 seconds)) {
        process.isAlive shouldBe false
        process.exitValue() shouldBe 0
      }
    }

    "process calls to reconfigure before and after being operational" in {
      val process = Runtime.getRuntime.exec(createProcessCommand("kamon.ReconfiguringBeforeInit"))

      eventually(timeout(7 seconds)) {
        process.isAlive shouldBe false
        process.exitValue() shouldBe 0
      }
    }
  }

  def createProcessCommand(mainClass: String): String = {
    System.getProperty("java.home") + File.separator + "bin" + File.separator + "java" +
    " -cp " + System.getProperty("java.class.path") + " " + mainClass
  }
}

class DummyMetricReporter extends kamon.module.MetricReporter {
  override def stop(): Unit = {}
  override def reconfigure(config: Config): Unit = {}
  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {}
}

class DummySpanReporter extends kamon.module.SpanReporter {
  override def stop(): Unit = {}
  override def reconfigure(config: Config): Unit = {}
  override def reportSpans(spans: Seq[Span.Finished]): Unit = {}
}

object KamonWithRunningReporter extends App {
  Kamon.registerModule("dummy metric reporter", new DummyMetricReporter())
  Kamon.registerModule("dummy span reporter", new DummySpanReporter())
}

object KamonWithTemporaryReporter extends App {
  Kamon.registerModule("dummy metric reporter", new DummyMetricReporter())
  Kamon.registerModule("dummy span reporter", new DummySpanReporter())

  Thread.sleep(5000)
  Kamon.stop()
}

object UsingKamonApisWithoutInit extends App {
  Kamon.counter("my-counter")
  Kamon.runWithContextTag("hello", "kamon") {
    Kamon.currentSpan().takeSamplingDecision()
  }

  val allKamonThreadNames = Thread.getAllStackTraces
    .keySet()
    .asScala
    .filter(_.getName.startsWith("kamon"))

  if (allKamonThreadNames.nonEmpty)
    sys.error("Kamon shouldn't start or create threads until init is called")
}

object ReconfiguringBeforeInit extends App {
  Kamon.reconfigure(ConfigFactory.load())
  Kamon.init()
  Kamon.stop()
  Kamon.reconfigure(ConfigFactory.load())
}
