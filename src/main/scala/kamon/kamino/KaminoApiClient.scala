package kamon.kamino

import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import kamino.IngestionV1.{Goodbye, Hello, MetricBatch, SpanBatch}
import kamon.Kamon

import scala.annotation.tailrec
import scala.util.{Success, Try}

class KaminoApiClient(config: KaminoConfiguration) {
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
    val timeSinceLastPost = Duration.between(lastAttempt, Kamon.clock().instant())

    val request = () => {
      val reqBody = RequestBody.create(MediaType.parse("application/octet-stream"), body)
      val request = new Request.Builder().url(apiUrl).post(reqBody).build
      val response = client.newCall(request).execute
      response.body().close()
      response.code()
    }

    if(timeSinceLastPost.compareTo(config.clientBackoff) < 0) backoff(config.clientBackoff.toMillis)

    def tryPosting: Try[Int] = Try {
      lastAttempt = clock.instant()
      request()
    }

    tryPosting match {
      case Success(200)  => // All good.
      case _ if retries > 0 =>
        backoff(config.retryBackoff.toMillis)
        postWithRetry(body, apiUrl, retries - 1)
      case _ => // Nothing to do.
    }
  }

  private def createHttpClient(config: KaminoConfiguration): OkHttpClient = {
    new OkHttpClient.Builder()
      .connectTimeout(config.connectionTimeout.toMillis, TimeUnit.MILLISECONDS)
      .readTimeout(config.readTimeout.toMillis, TimeUnit.MILLISECONDS)
      .build()
  }


  private def backoff(millis: Long) = {
    Thread.sleep(millis)
  }
}
