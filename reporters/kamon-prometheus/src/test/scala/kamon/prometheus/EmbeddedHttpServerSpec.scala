package kamon.prometheus

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.FileNotFoundException
import java.net.{URL, URLConnection}
import java.util.zip.GZIPInputStream
import scala.jdk.CollectionConverters._

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
      val metrics = httpGetMetrics("/metrics").content
      // assert
      metrics shouldBe "# The kamon-prometheus module didn't receive any data just yet.\n"
    }

    "provide the metrics on GET to /metrics with empty data" in {
      // arrange
      testee.reportPeriodSnapshot(emptyPeriodSnapshot)
      // act
      val metrics = httpGetMetrics("/metrics").content
      // assert
      metrics shouldBe ""
    }

    "provide the metrics on GET to /metrics with data" in {
      // arrange
      testee.reportPeriodSnapshot(counter("jvm.mem"))
      // act
      val metrics = httpGetMetrics("/metrics").content
      // assert
      metrics shouldBe "# TYPE jvm_mem_total counter\njvm_mem_total 1.0\n"
    }

    "provide the metrics on GET to /metrics with data after reconfigure" in {
      // arrange
      testee.reconfigure(testConfig)
      testee.reportPeriodSnapshot(counter("jvm.mem"))
      // act
      val metrics = httpGetMetrics("/metrics").content
      // assert
      println(metrics)
      metrics shouldBe "# TYPE jvm_mem_total counter\njvm_mem_total 2.0\n"
    }

    "respect gzip Content-Encoding headers" in {
      // arrange
      testee.reportPeriodSnapshot(counter("jvm.mem"))
      // act
      val metrics = httpGetMetrics("/metrics")
      val gzippedMetrics = httpGetMetrics("/metrics", useGzipEncoding = true)
      // assert
      metrics.contentLength should be > gzippedMetrics.contentLength
    }

    "property set Content-Type" in {
      // arrange
      testee.reportPeriodSnapshot(counter("jvm.mem"))
      // act
      val metrics = httpGetMetrics("/metrics")
      val gzippedMetrics = httpGetMetrics("/metrics", useGzipEncoding = true)
      // assert
      metrics.headers("Content-type").head shouldBe "text/plain; charset=UTF-8"
      gzippedMetrics.headers("Content-type").head shouldBe "text/plain; charset=UTF-8"
    }

    "respect the path configuration" in {
      httpGetMetrics("/metrics").content should not be empty
      assertThrows[FileNotFoundException] {
        httpGetMetrics("/new-metrics")
      }

      testee.reconfigure(changeEndpoint("/new-metrics"))
      httpGetMetrics("/new-metrics").content should not be empty

      assertThrows[FileNotFoundException] {
        httpGetMetrics("/metrics").content
      }
    }
  }

  private case class Result(content: String, headers: Map[String, List[String]], contentLength: Int)

  private def httpGetMetrics(endpoint: String, useGzipEncoding: Boolean = false): Result = {
    val url = new URL(s"http://127.0.0.1:$port$endpoint")
    val connection = url.openConnection
    val stream = if (useGzipEncoding) {
      connection.setRequestProperty("Accept-Encoding", "gzip")
      new GZIPInputStream(connection.getInputStream)
    } else connection.getInputStream
    val src = scala.io.Source.fromInputStream(stream)
    if (useGzipEncoding) {
      connection.getRequestProperty("Accept-Encoding") shouldBe "gzip"
    }
    try {
      val content = src.mkString
      Result(
        content,
        connection.getHeaderFields.asScala.toMap.view.mapValues(_.asScala.toList).toMap,
        connection.getContentLength
      )
    } finally stream.close()
  }

  private def changeEndpoint(path: String): Config = {
    ConfigFactory.parseString(
      s"""kamon.prometheus.embedded-server.metrics-path = ${path}"""
    ).withFallback(testConfig)
  }
}
