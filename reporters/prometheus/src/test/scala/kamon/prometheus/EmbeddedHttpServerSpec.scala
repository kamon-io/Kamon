package kamon.prometheus

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.FileNotFoundException
import java.net.URL
import java.util.zip.GZIPInputStream

class SunHttpServerSpecSuite extends EmbeddedHttpServerSpecSuite {
  override def testConfig: Config = ConfigFactory.load()
}

abstract class EmbeddedHttpServerSpecSuite extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with KamonTestSnapshotSupport
    with Eventually {
  protected def testConfig: Config

  protected def port: Int = 9095

  private var testee: PrometheusReporter = _

  override def beforeAll(): Unit = testee = new PrometheusReporter(initialConfig = testConfig)

  override def afterAll(): Unit = testee.stop()

  "the embedded sun http server" should {
    "provide no data comment on GET to /metrics when no data loaded yet" in {
      // act
      val metrics = httpGetMetrics("/metrics")
      // assert
      metrics shouldBe "# The kamon-prometheus module didn't receive any data just yet.\n"
    }

    "provide the metrics on GET to /metrics with empty data" in {
      // arrange
      testee.reportPeriodSnapshot(emptyPeriodSnapshot)
      // act
      val metrics = httpGetMetrics("/metrics")
      // assert
      metrics shouldBe ""
    }

    "provide the metrics on GET to /metrics with data" in {
      // arrange
      testee.reportPeriodSnapshot(counter("jvm.mem"))
      // act
      val metrics = httpGetMetrics("/metrics")
      // assert
      metrics shouldBe "# TYPE jvm_mem_total counter\njvm_mem_total 1.0\n"
    }

    "provide the metrics on GET to /metrics with data after reconfigure" in {
      // arrange
      testee.reconfigure(testConfig)
      testee.reportPeriodSnapshot(counter("jvm.mem"))
      // act
      val metrics = httpGetMetrics("/metrics")
      // assert
      metrics shouldBe "# TYPE jvm_mem_total counter\njvm_mem_total 2.0\n"
    }

    "respect gzip Content-Encoding headers" in {
      // arrange
      testee.reportPeriodSnapshot(counter("jvm.mem"))
      // act
      val metrics = httpGetMetrics("/metrics")
      val gzippedMetrics = httpGetGzippedMetrics("/metrics")
      // assert
      metrics.length should be > gzippedMetrics.length
    }

    "respect the path configuration" in {
      httpGetMetrics("/metrics") should not be empty
      assertThrows[FileNotFoundException] {
        httpGetMetrics("/new-metrics")
      }

      testee.reconfigure(changeEndpoint("/new-metrics"))
      httpGetMetrics("/new-metrics") should not be empty

      assertThrows[FileNotFoundException] {
        httpGetMetrics("/metrics")
      }
    }
  }

  private def httpGetMetrics(endpoint: String): String = {
    val url = new URL(s"http://127.0.0.1:$port$endpoint")
    val src = scala.io.Source.fromURL(url)
    try src.mkString
    finally src.close()
  }

  private def httpGetGzippedMetrics(endpoint: String): String = {
    val url = new URL(s"http://127.0.0.1:$port$endpoint")
    val connection = url.openConnection
    connection.setRequestProperty("Accept-Encoding", "gzip")
    val gzipStream = new GZIPInputStream(connection.getInputStream)
    val src = scala.io.Source.fromInputStream(gzipStream)
    connection.getRequestProperty("Accept-Encoding") shouldBe "gzip"
    try src.getLines.mkString
    finally gzipStream.close()
  }

  private def changeEndpoint(path: String): Config = {
    ConfigFactory.parseString(
      s"""kamon.prometheus.embedded-server.metrics-path = ${path}"""
    ).withFallback(testConfig)
  }
}
