package kamon.graphite

import kamon.metric.PeriodSnapshot
import kamon.tag.TagSet
import kamon.testkit.MetricSnapshotBuilder
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory

import java.io.InputStream
import java.net.{InetSocketAddress, ServerSocket}
import java.time.Instant
import java.util.Scanner
import java.util.concurrent.{CopyOnWriteArrayList, CountDownLatch, TimeUnit}
import scala.collection.JavaConverters._

class GraphiteReporterSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with Eventually {
  private val graphite = new GraphiteServer()

  override def beforeAll(): Unit = graphite.start()
  override def afterAll(): Unit = graphite.stop()

  "the GraphiteReporter" should {
    val from = Instant.ofEpochSecond(1517000974)
    val to = Instant.ofEpochSecond(1517000993)

    val periodSnapshot = PeriodSnapshot.apply(
      from,
      to,
      counters = List(MetricSnapshotBuilder.counter("custom.user.counter", TagSet.of("tag.1", "value.1.2"), 42)),
      gauges = List.empty,
      histograms = List.empty,
      timers = List.empty,
      rangeSamplers = List.empty
    )

    val lines = graphite.lineListener(1)
    val reporter = new GraphiteReporter()

    "send counter metrics to a server socket" in {
      reporter.reportPeriodSnapshot(periodSnapshot)

      lines.awaitLines() shouldBe List(
        "kamon-graphite.custom_user_counter.count;special-shouldsee-tag=bla;service=kamon-application;tag.1=value.1.2 42 1517000993"
      )
    }

    reporter.stop()
  }
}

class GraphiteServer {
  private val listeners = new CopyOnWriteArrayList[LineListener]
  private val started = new CountDownLatch(1)
  private lazy val t = new GraphiteTcpSocketListener(2003, listeners, started)

  def start(): Unit = {
    t.start()
    started.await()
  }

  def stop(): Unit =
    t.close()

  def lineListener(expectedLines: Int): LineListener = {
    val listener = new LineListener(expectedLines)
    listeners.add(listener)
    listener
  }
}

class GraphiteTcpSocketListener(port: Int, listeners: java.util.List[LineListener], started: CountDownLatch)
    extends Thread {
  private val log = LoggerFactory.getLogger(classOf[GraphiteTcpSocketListener])
  private val ss = new CopyOnWriteArrayList[InputStream]
  private val serverSocket = new ServerSocket()

  override def run(): Unit = {
    try {
      val address = new InetSocketAddress(port)
      log.debug("starting graphite simulator serversocket {}", address)
      serverSocket.bind(address)
      started.countDown()
      val is = serverSocket.accept().getInputStream
      ss.add(is)
      val lineReader = new Scanner(is, GraphiteSender.GraphiteEncoding.name())
      while (lineReader.hasNextLine) {
        val line = lineReader.nextLine()
        listeners.asScala.foreach(_.putLine(line))
      }
    } finally {
      log.debug("stopping graphite simulator")
      serverSocket.close()
    }
  }

  def close(): Unit = {
    try {
      ss.asScala.foreach(_.close())
    } finally {
      serverSocket.close()
    }
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
