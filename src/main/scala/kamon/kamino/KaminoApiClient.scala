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
  private val logger = LoggerFactory.getLogger(classOf[KaminoMetricReporter])

  private val client = createHttpClient(config)
  private var lastAttempt: Instant = Instant.EPOCH

  def postIngestion(metricBatch: MetricBatch): Unit =
    postWithRetry(metricBatch.toByteArray, config.ingestionRoute, config.ingestionRetries)

  def postHello(hello: Hello): Unit =
    postWithRetry(hello.toByteArray, config.bootMark, config.bootRetries)

  def postGoodbye(goodBye: Goodbye): Unit =
    postWithRetry(goodBye.toByteArray, config.shutdownMark, config.shutdownRetries)

  def postSpans(spanBatch: SpanBatch): Unit =
    postWithRetry(spanBatch.toByteArray, config.tracingRoute, config.tracingRetries)

  def stop(): Unit = client.dispatcher().executorService().shutdown()


  @tailrec
  private def postWithRetry(body: Array[Byte], apiUrl: String, retries: Int): Unit = {
    val clock = Kamon.clock()

    val timeSinceLastPost = Duration.between(lastAttempt, clock.instant())
    if(timeSinceLastPost.compareTo(config.clientBackoff) < 0) backoff

    val request: () => Response = () => {
      val reqBody = RequestBody.create(MediaType.parse("application/octet-stream"), body)
      val request = new Request.Builder().url(apiUrl).post(reqBody).build
      client.newCall(request).execute
    }

    def tryPosting: Try[Response] = Try {
      lastAttempt = clock.instant()
      request()
    }

    def parseResponse(response: Response): Try[IngestionResponse] = Try {
      if(response.code() == 200) {
        response.body().close()
        IngestionResponse
          .newBuilder()
          .setStatus(OK)
          .build()
      } else {
        IngestionResponse.parseFrom(response.body().bytes())
      }
    }

    tryPosting.flatMap(parseResponse) match {
      case Success(ingestionResult) =>
        ingestionResult.getStatus match {
          case OK =>
            logger.info("Snapshot ingested")
          case STALE =>
            logger.warn("Ingestion declined, stale data")
          case BLOCKED =>
            logger.warn("Ingestion declined, plan limits reached")
          case UNAUTHORIZED =>
            logger.error("Ingestion declined, missing or wrong API key")
          case CORRUPTED =>
            logger.warn("Ingestion declined, illegal batch")
          case ERROR =>
            logger.warn("Ingestion declined, unknown error")
            backoff
            postWithRetry(body, apiUrl, retries - 1)
        }
      case Failure(connectionException) if retries > 0 =>
        logger.error(s"Connection error, retrying... ($retries left) ${connectionException.getMessage}")
        backoff
        postWithRetry(body, apiUrl, retries - 1)
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
