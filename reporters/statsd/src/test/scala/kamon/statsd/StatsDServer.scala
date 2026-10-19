package kamon.statsd

import java.io.IOException
import java.net.{DatagramPacket, DatagramSocket, InetAddress, ServerSocket, SocketException}

import kamon.statsd.StatsDServer._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Try

/**
  * Simulation of a statsD server that collects packets and metric coming from an UDP channel.
  * @param port the UDP port the server is listening to (by default this port is randomized).
  */
class StatsDServer(val port: Int = selectRandomPort) {

  private val logger = LoggerFactory.getLogger(classOf[StatsDServer])

  logger.info("Create StatsD Server")

  private val socket = new DatagramSocket(port)
  private var packets = List[Packet]()
  private var packetRequests = List[PacketRequest]()

  def start(): Unit = run()

  private def run(): Unit = Future {
    logger.info(s"Start StatsD Server (port: $port)")
    var isRunning = true
    while (isRunning) {
      try {
        val buffer = new Array[Byte](1024)
        val packet = new DatagramPacket(buffer, buffer.length)
        socket.receive(packet)
        val data = new String(packet.getData.take(packet.getLength))
        addPacket(data)
      } catch {
        case e: SocketException =>
          isRunning = false
      }
    }
    logger.info("Stop StatsD Server")
  }

  private def addPacket(packet: String): Unit = synchronized {
    packets = Packet(packet) :: packets
    val (satisfiedRequests, notSatisfiedRequests) = packetRequests.partition(_.condition(packets))
    satisfiedRequests.foreach(_.promise.success(packets))
    packetRequests = notSatisfiedRequests
  }

  case class PacketRequest(promise: Promise[List[Packet]], condition: (List[Packet]) => Boolean)

  def getPackets(condition: List[Packet] => Boolean, waitFor: Duration = 2.seconds): List[Packet] = {
    val promise = Promise[List[Packet]]()
    synchronized {
      if (condition(packets)) promise.success(packets)
      else packetRequests = PacketRequest(promise, condition) :: packetRequests
    }
    Await.result(promise.future, waitFor)
  }

  def getPacket(condition: Packet => Boolean, waitFor: Duration = 2.seconds): Packet = {
    getPackets(_.exists(condition), waitFor).find(condition).get
  }

  def stop(): Unit = {
    socket.close()
  }

  def clear(): Unit = synchronized {
    packets = Nil
    packetRequests = Nil
  }

}

object StatsDServer {

  case class Packet(metrics: List[Metric]) {

    def getMetric(condition: Metric => Boolean): Option[Metric] = metrics.find(condition)
    def hasMetric(condition: Metric => Boolean): Boolean = metrics.exists(condition)

  }

  object Packet {
    def apply(raw: String): Packet = {
      val metrics = raw.split("\n").filter(_.nonEmpty).flatMap(Metric.parse)
      Packet(metrics.toList)
    }
  }

  /**
    * The metric format is [metric_name]:[value]|[metric_type]|@[sample_rate].
    */
  case class Metric(name: String, value: String, metricType: String, sample: Option[String] = None)

  object Metric {

    def parse(raw: String): List[Metric] = {
      val nameAndValues = raw.split(":").toList
      val name = nameAndValues.head
      val values = nameAndValues.tail.map(_.split("\\|"))
      values.map {
        case Array(value, metricType) =>
          Metric(name, value, metricType)
        case Array(value, metricType, sample) =>
          Metric(name, value, metricType, Some(sample.tail))
      }
    }

  }

  private def selectRandomPort: Int = {
    var socket: ServerSocket = null
    try {
      socket = new ServerSocket(0)
      socket.setReuseAddress(true)
      socket.getLocalPort
    } catch {
      case e: IOException =>
        throw new IllegalStateException("Impossible to find a free port", e)
    } finally {
      Try { socket.close() }
    }
  }

}
