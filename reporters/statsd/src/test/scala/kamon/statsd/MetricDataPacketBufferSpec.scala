package kamon.statsd

import kamon.statsd.StatsDReporter.MetricDataPacketBuffer
import kamon.statsd.StatsDServer.Metric
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel

class MetricDataPacketBufferSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with BeforeAndAfter {

  val statsDServer = new StatsDServer()
  val address = new InetSocketAddress("localhost", statsDServer.port)
  val hugePacketSize = 1024
  val maxPacketsPerMilli = 3

  "MetricDataPacketBuffer" should {

    "flush a single metric in one udp packet" in {
      val channel = DatagramChannel.open()
      val buffer = new MetricDataPacketBuffer(hugePacketSize, maxPacketsPerMilli, channel, address)
      buffer.appendMeasurement("counter", "1.0|c")
      buffer.flush()
      val packet = statsDServer.getPacket(_.hasMetric(_.name == "counter"))
      packet.metrics should have size 1
      packet.metrics should contain(Metric("counter", "1.0", "c"))
    }

    "flush multiple metrics in the same udp packet" in {
      val channel = DatagramChannel.open()
      val buffer = new MetricDataPacketBuffer(hugePacketSize, maxPacketsPerMilli, channel, address)
      buffer.appendMeasurement("counter", "1.0|c")
      buffer.appendMeasurement("other_counter", "2.0|c")
      buffer.flush()
      val packet = statsDServer.getPacket(_.hasMetric(_.name == "counter"))
      packet.metrics should have size 2
      packet.metrics should contain(Metric("counter", "1.0", "c"))
      packet.metrics should contain(Metric("other_counter", "2.0", "c"))
    }

    "flush multiple metrics in different udp packets when max packet size is reached" in {
      val channel = DatagramChannel.open()
      val buffer = new MetricDataPacketBuffer(maxPacketSizeInBytes = 20, maxPacketsPerMilli, channel, address)
      buffer.appendMeasurement("counter", "1.0|c")
      buffer.appendMeasurement("other_counter", "2.0|c")
      buffer.flush()
      val packet = statsDServer.getPacket(_.hasMetric(_.name == "counter"))
      packet.metrics should have size 1
      packet.metrics should contain(Metric("counter", "1.0", "c"))
      val otherPacket = statsDServer.getPacket(_.hasMetric(_.name == "other_counter"))
      otherPacket.metrics should have size 1
      otherPacket.metrics should contain(Metric("other_counter", "2.0", "c"))
    }

    "flush when max packet size is reached" in {
      val channel = DatagramChannel.open()
      val buffer = new MetricDataPacketBuffer(maxPacketSizeInBytes = 20, maxPacketsPerMilli, channel, address)
      buffer.appendMeasurement("counter", "1.0|c")
      buffer.appendMeasurement("other_counter", "2.0|c")
      val packet = statsDServer.getPacket(_.hasMetric(_.name == "counter"))
      packet.metrics should have size 1
      packet.metrics should contain(Metric("counter", "1.0", "c"))
    }

    "flush same metric in one udp packet because is compressed" in {
      val channel = DatagramChannel.open()
      val buffer = new MetricDataPacketBuffer(maxPacketSizeInBytes = 20, maxPacketsPerMilli, channel, address)
      buffer.appendMeasurement("counter", "1.0|c")
      buffer.appendMeasurement("counter", "2.0|c")
      buffer.flush()
      val packet = statsDServer.getPacket(_.hasMetric(_.name == "counter"))
      packet.metrics should have size 2
      val metrics = packet.metrics.filter(_.name == "counter")
      metrics should have size 2
      metrics should contain(Metric("counter", "1.0", "c"))
      metrics should contain(Metric("counter", "2.0", "c"))
    }

    "flush different metric in two udp packets because not compressed" in {
      val channel = DatagramChannel.open()
      val buffer = new MetricDataPacketBuffer(maxPacketSizeInBytes = 20, maxPacketsPerMilli, channel, address)
      buffer.appendMeasurement("counter", "1.0|c")
      buffer.appendMeasurement("count3r", "2.0|c")
      buffer.flush()
      val packet = statsDServer.getPacket(_.hasMetric(_.name == "counter"))
      packet.metrics should have size 1
      val otherPacket = statsDServer.getPacket(_.hasMetric(_.name == "count3r"))
      otherPacket.metrics should have size 1
    }

    "flush same metric in two udp packets when max packet size is reached" in {
      val channel = DatagramChannel.open()
      val buffer = new MetricDataPacketBuffer(maxPacketSizeInBytes = 20, maxPacketsPerMilli, channel, address)
      buffer.appendMeasurement("counter", "1.0|c")
      buffer.appendMeasurement("counter", "2.0|c")
      buffer.appendMeasurement("counter", "3.0|c")
      buffer.flush()
      val packets = statsDServer.getPackets(_.size == 2)
      packets.exists(_.metrics.size == 2) should be(true)
      packets.exists(_.metrics.size == 1) should be(true)
    }

  }

  override def beforeAll(): Unit = {
    statsDServer.start()
  }

  before {
    statsDServer.clear()
  }

  override def afterAll(): Unit = {
    statsDServer.stop()
  }

}
