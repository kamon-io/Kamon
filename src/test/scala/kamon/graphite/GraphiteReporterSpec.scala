package kamon.graphite

import java.io.InputStream
import java.net.ServerSocket
import java.time.Instant
import java.util.Scanner
import java.util.concurrent.{CopyOnWriteArrayList, CountDownLatch, TimeUnit}

import kamon.metric.PeriodSnapshot
import kamon.tag.TagSet
import kamon.testkit.MetricSnapshotBuilder
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class GraphiteReporterSpec extends WordSpec with BeforeAndAfterAll with Matchers with Eventually {
  private val graphite = new GraphiteServer()

  override def beforeAll(): Unit = graphite.start()
  override def afterAll(): Unit = graphite.stop()

  "the GraphiteReporter" should {
    val from = Instant.ofEpochSecond(1517000974)
    val to = Instant.ofEpochSecond(1517000993)

    val periodSnapshot = PeriodSnapshot.apply(
      from, to,
      counters = List(MetricSnapshotBuilder.counter("custom.user.counter", TagSet.of("tag.1", "value.1.2"), 42)),
      gauges = List.empty, histograms = List.empty, timers = List.empty, rangeSamplers = List.empty
    )

    val lines = graphite.lineListener(1)
    val reporter = new GraphiteReporter()

    "send counter metrics to a server socket" in {
      reporter.reportPeriodSnapshot(periodSnapshot)

      lines.awaitLines() shouldBe List("kamon-graphite.custom_user_counter.count;special-shouldsee-tag=bla;service=kamon-application;tag.1=value.1.2 42 1517000993")
    }

    reporter.stop()
  }
}

class GraphiteServer {
  private val log = LoggerFactory.getLogger(classOf[GraphiteSender])
  private val listeners = new CopyOnWriteArrayList[LineListener]
  private val ss = new CopyOnWriteArrayList[InputStream]
  private val t = new Thread(() => {
    val serverSocket = new ServerSocket(2003)
    log.debug("starting graphite simulator serversocket {}:{}", serverSocket.getInetAddress, serverSocket.getLocalPort)
    try {
      val is = serverSocket.accept().getInputStream
      ss.add(is)
      val lineReader = new Scanner(is, GraphiteSender.GraphiteEncoding)
      while (lineReader.hasNextLine) {
        val line = lineReader.nextLine()
        listeners.asScala.foreach(_.putLine(line))
      }
    }
    catch {
      case _: Exception =>
        log.debug("stopping graphite simulator")
        serverSocket.close()
    }
  })

  def start(): Unit =
    t.start()

  def stop(): Unit = {
    ss.asScala.foreach(_.close())
    t.join()
  }

  def lineListener(expectedLines: Int): LineListener = {
    val listener = new LineListener(expectedLines)
    listeners.add(listener)
    listener
  }
}

class LineListener(lineCount: Int) {
  private val counter = new CountDownLatch(lineCount)
  private val lines = new CopyOnWriteArrayList[String]
  def putLine(l: String): Unit = this.synchronized {
    lines.add(l)
    counter.countDown()
  }

  def awaitLines(): List[String] = {
    counter.await(1, TimeUnit.SECONDS)
    lines.asScala.toList
  }
}