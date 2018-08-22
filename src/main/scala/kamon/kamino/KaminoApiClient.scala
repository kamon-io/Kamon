package kamon.kamino

import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import java.net.{InetSocketAddress, Proxy}

import okhttp3._
import kamino.IngestionV1._
import IngestionStatus._
import kamon.Kamon
import kamon.kamino.reporters.KaminoMetricReporter
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class KaminoApiClient(config: KaminoConfiguration) {
  private val logger = LoggerFactory.getLogger(classOf[KaminoApiClient])

  private val client = createHttpClient(config)
  private var lastAttempt: Instant = Instant.EPOCH
  private val apiKeyHeaderName = "kamino-api-key"

  def postIngestion(metricBatch: MetricBatch): Unit =
    postWithRetry(metricBatch.toByteArray, "metrics-ingestion", config.ingestionRoute, config.ingestionRetries)

  def postHello(hello: Hello): Unit =
    postWithRetry(hello.toByteArray, "hello", config.bootMark, config.bootRetries)

  def postGoodbye(goodBye: Goodbye): Unit =
    postWithRetry(goodBye.toByteArray, "goodbye", config.shutdownMark, config.shutdownRetries)

  def postSpans(spanBatch: SpanBatch): Unit =
    postWithRetry(spanBatch.toByteArray, "spans-ingestion", config.tracingRoute, config.tracingRetries)

  def stop(): Unit = client.dispatcher().executorService().shutdown()


  @tailrec
  private def postWithRetry(body: Array[Byte], endpointName: String, apiUrl: String, retries: Int): Unit = {
    val clock = Kamon.clock()

    val timeSinceLastPost = Duration.between(lastAttempt, clock.instant())
    if(timeSinceLastPost.compareTo(config.clientBackoff) < 0) backoff

    val request: () => Response = () => {
      val reqBody = RequestBody.create(MediaType.parse("application/octet-stream"), body)
      val request = new Request.Builder()
        .url(apiUrl)
        .post(reqBody)
        .addHeader(apiKeyHeaderName, config.apiKey)
        .build()

      client.newCall(request).execute
    }

    def tryPosting: Try[Response] = Try {
      lastAttempt = clock.instant()
      request()
    }

    def parseResponse(response: Response): Try[IngestionResponse] = Try {
      val respBuilder = IngestionResponse.newBuilder()
      response.code() match {
        case 200 =>
          respBuilder
            .setStatus(OK)
            .build()
        case 490 =>
          IngestionResponse
            .parseFrom(response.body().bytes())
        case _ =>
          respBuilder
            .setStatus(IngestionStatus.ERROR)
            .build()
      }
    }

    tryPosting.flatMap(parseResponse) match {
      case Success(ingestionResult) =>
        ingestionResult.getStatus match {
          case OK =>
            logger.trace("[{}] request succeeded", endpointName)
          case STALE =>
            logger.warn("[{}] request declined, stale data", endpointName)
          case BLOCKED =>
            logger.warn("[{}] request declined, plan limits reached", endpointName)
          case UNAUTHORIZED =>
            logger.error("[{}] request declined, missing or wrong API key", endpointName)
          case CORRUPTED =>
            logger.warn("[{}] request declined, illegal batch", endpointName)
          case ERROR if retries > 0 =>
            logger.warn("[{}] request declined, unknown error", endpointName)
            backoff
            postWithRetry(body, endpointName, apiUrl, retries - 1)
          case ERROR =>
            logger.warn("[{}] request declined, unknown error", endpointName)
        }
      case Failure(connectionException) if retries > 0 =>
        logger.warn(s"Connection error, retrying... ($retries left) ${connectionException.getMessage}")
        backoff
        postWithRetry(body, endpointName, apiUrl, retries - 1)
      case Failure(connectionException) =>
        logger.error(s"Ingestion error, no retries, dropping snapshot... ${connectionException.getMessage}")
    }

  }

  private def createHttpClient(config: KaminoConfiguration): OkHttpClient = {
    val builder = new OkHttpClient.Builder()
      .connectTimeout(config.connectionTimeout.toMillis, TimeUnit.MILLISECONDS)
      .readTimeout(config.readTimeout.toMillis, TimeUnit.MILLISECONDS)

    config.proxy.foreach { pt =>
      builder.proxy(
        new Proxy(pt, new InetSocketAddress(config.proxyHost, config.proxyPort))
      )
    }

    builder.build()
  }

  private def backoff = {
    Thread.sleep(config.clientBackoff.toMillis)
  }
}
