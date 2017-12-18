package kamon.zipkin

import java.net.{Inet4Address, InetAddress}
import java.nio.ByteBuffer
import java.util.Collections

import com.typesafe.config.{Config, ConfigFactory}
import kamon.{Kamon, SpanReporter}
import kamon.trace.IdentityProvider.Identifier
import kamon.trace.Span.TagValue.{False, Number, True, String => TString}
import kamon.trace.{Span => KamonSpan}
import org.slf4j.LoggerFactory
import zipkin.{Annotation, BinaryAnnotation, Constants, Endpoint, TraceKeys, Span => ZipkinSpan}
import zipkin.reporter.okhttp3.OkHttpSender
import zipkin.reporter.{AsyncReporter, Encoding}

import scala.util.Try


sealed trait SpanKind
case object ServerSpan extends SpanKind
case object ClientSpan extends SpanKind
case object LocalSpan extends SpanKind


object ZipkinReporter {
  private val KEY_HOST = "kamon.zipkin.host"
  private val KEY_PORT = "kamon.zipkin.port"
  private val config = ConfigFactory.load


  private val KIND_KEY = "span.kind"

  private object PEER_KEYS {
    val HOST     = "peer.host"
    val PORT     = "peer.port"
    val SERVICE  = "peer.service"
    val IPV4     = "peer.ipv4"
    val METHOD   = "http.method"
    val URL      = "http.url"
  }



}

case class Peer(
 host:    Option[String]  = None,
 ipv4:    Option[Int]     = None,
 port:    Option[Int]     = None,
 service: Option[String]  = None,
 method:  Option[String]  = None,
 url:     Option[String]  = None
)

class ZipkinReporter() extends SpanReporter {
  import ZipkinReporter._

  private var localEndpoint = buildEndpoint
  private var reporter      = buildReporter

  private val logger = LoggerFactory.getLogger(classOf[ZipkinReporter])

  def checkJoinParameter = {
    val enabled = config.hasPath("kamon.trace.join-remote-parents-with-same-span-id")
    if(!enabled)
      logger.warn("For full Zipkin compatibility enable `kamon.trace.join-remote-parents-with-same-span-id` to preserve span id across client calls")
  }

  checkJoinParameter


  override def reportSpans(spans: Seq[KamonSpan.FinishedSpan]): Unit =
    spans.map(convertSpan).foreach(reporter.report)


  def convertSpan(internalSpan: KamonSpan.FinishedSpan): ZipkinSpan = {
    val builder = ZipkinSpan.builder()

    val duration = internalSpan.endTimestampMicros - internalSpan.startTimestampMicros

    builder
      .traceId(convertIdentifier(internalSpan.context.traceID))
      .id(convertIdentifier(internalSpan.context.spanID))
      .parentId(convertIdentifier(internalSpan.context.parentID))
      .duration(duration)
      .name(internalSpan.operationName)
      .timestamp(internalSpan.startTimestampMicros)

    val spanKind = getKind(internalSpan)

    val (coreAnnotations, coreBinaryAnnotations) = spanKind match {
      case ServerSpan =>  annotateServerSpan(internalSpan)
      case ClientSpan =>  annotateClientSpan(internalSpan)
      case LocalSpan =>   annotateLocalSpan(internalSpan)
    }

    val binaryAnnotations = internalSpan.tags.map { case (tag, tagValue) =>
      val (tpe, value) = toZipkinTag(tagValue)
      BinaryAnnotation.create(tag, value, tpe, localEndpoint)
    }

    coreAnnotations.foreach(builder.addAnnotation)
    (coreBinaryAnnotations ++ binaryAnnotations).foreach(builder.addBinaryAnnotation)

    builder.build()
  }

  private def toZipkinTag(value: KamonSpan.TagValue): (BinaryAnnotation.Type, Array[Byte]) = value match {
      case True           => (BinaryAnnotation.Type.BOOL,   BigInt(1).toByteArray)
      case False          => (BinaryAnnotation.Type.BOOL,   BigInt(0).toByteArray)
      case TString(value)  => (BinaryAnnotation.Type.STRING, value.toCharArray().map(_.toByte))
      case Number(value)  => (BinaryAnnotation.Type.I64,    BigInt(value).toByteArray)
    }


  private def annotateServerSpan(internal: KamonSpan.FinishedSpan): (Seq[Annotation], Seq[BinaryAnnotation]) = {
    val peer = extractPeer(internal)

    val serverReceive = Annotation.create(internal.startTimestampMicros, Constants.SERVER_RECV, localEndpoint)
    val serverSend    = Annotation.create(internal.endTimestampMicros, Constants.SERVER_SEND, localEndpoint)
    val address = BinaryAnnotation.create(
      Constants.SERVER_ADDR,
      peer.host.getOrElse(""),
      Endpoint.create(
        peer.service.getOrElse(""),
        peer.ipv4.getOrElse(0)
      )
    )

    val httpUrl = peer.url.map { url =>
      BinaryAnnotation.create(
        TraceKeys.HTTP_PATH,
        url.toCharArray.map(_.toByte),
        BinaryAnnotation.Type.STRING,
        localEndpoint
      )
    }

    val httpMethod = peer.method.map { method =>
      BinaryAnnotation.create(
        TraceKeys.HTTP_METHOD,
        method.toCharArray.map(_.toByte),
        BinaryAnnotation.Type.STRING,
        localEndpoint
      )
    }

    (Seq(serverReceive, serverSend), Seq(httpUrl, httpMethod, Some(address)).flatten)
  }

  private def annotateClientSpan(internal: KamonSpan.FinishedSpan): (Seq[Annotation], Seq[BinaryAnnotation]) = {
    val peer = extractPeer(internal)

    val clientSend    = Annotation.create(internal.startTimestampMicros, Constants.CLIENT_SEND, localEndpoint)
    val clientReceive = Annotation.create(internal.endTimestampMicros, Constants.CLIENT_RECV, localEndpoint)
    val address = BinaryAnnotation.create(
      Constants.CLIENT_ADDR,
      peer.host.getOrElse(""),
      Endpoint.create(
        peer.service.getOrElse(""),
        peer.ipv4.getOrElse(0)
      )
    )

    (Seq(clientSend, clientReceive), Seq(address))
  }

  private def annotateLocalSpan(internal: KamonSpan.FinishedSpan): (Seq[Annotation], Seq[BinaryAnnotation]) = {
    val local = BinaryAnnotation.create(Constants.LOCAL_COMPONENT, internal.operationName, localEndpoint)
    (Seq.empty, Seq(local))
  }

  private def extractPeer(internal: KamonSpan.FinishedSpan): Peer = {
    val tags    = internal.tags
    val host    = tags.get(PEER_KEYS.HOST).filter(_.isInstanceOf[TString]).map(_.asInstanceOf[TString].string)
    val ipv4    = tags.get(PEER_KEYS.IPV4).filter(_.isInstanceOf[Number]).map(_.asInstanceOf[Number].number).map(_.toInt)
    val port    = tags.get(PEER_KEYS.PORT).filter(_.isInstanceOf[Number]).map(_.asInstanceOf[Number].number).map(_.toInt)
    val service = tags.get(PEER_KEYS.SERVICE).filter(_.isInstanceOf[TString]).map(_.asInstanceOf[TString].string)
    val method  = tags.get(PEER_KEYS.METHOD).filter(_.isInstanceOf[TString]).map(_.asInstanceOf[TString].string)
    val url     = tags.get(PEER_KEYS.URL).filter(_.isInstanceOf[TString]).map(_.asInstanceOf[TString].string)

    Peer(
      host, ipv4, port, service, method, url)
  }


  private def getKind(internalSpan: KamonSpan.FinishedSpan): SpanKind = internalSpan.tags.get(KIND_KEY) match {
      case Some(TString("server"))  => ServerSpan
      case Some(TString("client"))  => ClientSpan
      case _                        => LocalSpan
    }

  private def convertIdentifier(identifier: Identifier): Long = Try {
    // Assumes that Kamon was configured to use the default identity generator.
    ByteBuffer.wrap(identifier.bytes).getLong
  }.getOrElse(0L)

  override def reconfigure(newConfig: Config): Unit = {
    localEndpoint = buildEndpoint
    reporter      = buildReporter
    checkJoinParameter
  }

  private def buildEndpoint: Endpoint = {
    val env = Kamon.environment

    val localAddress = Try(
      InetAddress.getByName(env.host)
    ).getOrElse(InetAddress.getLocalHost)

    val ipV4 = ByteBuffer.wrap(localAddress.getAddress).getInt

    Endpoint.create(env.service, ipV4)
  }

  private def buildReporter = {
    val zipkinHost  = config.getString(KEY_HOST)
    val zipkinPort  = config.getInt(KEY_PORT)

    AsyncReporter.create(
      OkHttpSender.builder()
        .endpoint(s"http://$zipkinHost:$zipkinPort/api/v1/spans")
        .encoding(Encoding.JSON)
        .build()
    )
  }

  override def start(): Unit = {
    logger.info("Started the Zipkin reporter.")
  }

  override def stop(): Unit = {
    logger.info("Stopped the Zipkin reporter.")
  }
}
