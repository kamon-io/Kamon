package kamon

import java.io.File
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.trace.Span
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar._

class KamonLifecycleSpec extends WordSpec with Matchers with Eventually {

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
  }


  def createProcessCommand(mainClass: String): String = {
    System.getProperty("java.home") + File.separator + "bin" + File.separator + "java" +
    " -cp " + System.getProperty("java.class.path") + " " + mainClass
  }
}

class DummyMetricReporter extends kamon.module.MetricReporter {
  override def start(): Unit = {}
  override def stop(): Unit = {}
  override def reconfigure(config: Config): Unit = {}
  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {}
}

class DummySpanReporter extends kamon.module.SpanReporter {
  override def start(): Unit = {}
  override def stop(): Unit = {}
  override def reconfigure(config: Config): Unit = {}
  override def reportSpans(spans: Seq[Span.FinishedSpan]): Unit = {}
}

object KamonWithRunningReporter extends App {
  Kamon.registerModule("dummy metric reporter", new DummyMetricReporter())
  Kamon.registerModule("dummy span reporter", new DummySpanReporter())
}

object KamonWithTemporaryReporter extends App {
  Kamon.registerModule("dummy metric reporter", new DummyMetricReporter())
  Kamon.registerModule("dummy span reporter", new DummySpanReporter())

  Thread.sleep(5000)
  Kamon.stopAllReporters()
}
