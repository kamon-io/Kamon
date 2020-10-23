/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.apm

import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import java.net.{InetSocketAddress, Proxy}

import okhttp3._
import kamino.IngestionV1._
import IngestionStatus._
import kamon.Kamon
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class KamonApmApiClient(settings: Settings) {
  private val _logger = LoggerFactory.getLogger(classOf[KamonApmApiClient])
  private val _httpClient = createHttpClient(settings)
  private val _apiKeyHeaderName = "kamino-api-key"
  private var _lastAttempt: Instant = Instant.EPOCH

  def postIngestion(metricBatch: MetricBatch): Unit =
    postWithRetry(metricBatch.toByteArray, "metrics-ingestion", settings.ingestionRoute, settings.ingestionRetries)

  def postHello(hello: Hello): Unit =
    postWithRetry(hello.toByteArray, "hello", settings.bootMark, settings.bootRetries)

  def postGoodbye(goodBye: Goodbye): Unit =
    postWithRetry(goodBye.toByteArray, "goodbye", settings.shutdownMark, settings.shutdownRetries)

  def postSpans(spanBatch: SpanBatch): Unit =
    postWithRetry(spanBatch.toByteArray, "spans-ingestion", settings.tracingRoute, settings.tracingRetries)

  def stop(): Unit = {
    _httpClient.dispatcher().executorService().shutdown()
    _httpClient.connectionPool().evictAll()
  }

  @tailrec
  private def postWithRetry(body: Array[Byte], endpointName: String, apiUrl: String, retries: Int): Unit = {
    val clock = Kamon.clock()

    val timeSinceLastPost = Duration.between(_lastAttempt, clock.instant())
    if(timeSinceLastPost.compareTo(settings.clientBackoff) < 0) backoff

    val request: () => Response = () => {
      val reqBody = RequestBody.create(MediaType.parse("application/octet-stream"), body)
      val request = new Request.Builder()
        .url(apiUrl)
        .post(reqBody)
        .addHeader(_apiKeyHeaderName, settings.apiKey)
        .build()

      _httpClient.newCall(request).execute
    }

    def tryPosting: Try[Response] = Try {
      _lastAttempt = clock.instant()
      request()
    }

    def parseResponse(response: Response): Try[IngestionResponse] = Try {
      val respBuilder = IngestionResponse.newBuilder()
      val body = response.body().bytes()
      response.code() match {
        case 200 =>
          respBuilder
            .setStatus(OK)
            .build()
        case 490 if body.nonEmpty =>
          IngestionResponse
            .parseFrom(body)
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
            _logger.trace("[{}] request succeeded", endpointName)
          case STALE =>
            _logger.warn("[{}] request declined, stale data", endpointName)
          case BLOCKED =>
            _logger.warn("[{}] request declined, plan limits reached", endpointName)
          case UNAUTHORIZED =>
            _logger.error("[{}] request declined, missing or wrong API key", endpointName)
          case CORRUPTED =>
            _logger.warn("[{}] request declined, illegal batch", endpointName)
          case ERROR if retries > 0 =>
            _logger.warn("[{}] request declined, unknown error", endpointName)
            backoff
            postWithRetry(body, endpointName, apiUrl, retries - 1)
          case ERROR =>
            _logger.warn("[{}] request declined, unknown error", endpointName)
        }
      case Failure(connectionException) if retries > 0 =>
        _logger.warn(s"Connection error, retrying... ($retries left) ${connectionException.getMessage}")
        backoff
        postWithRetry(body, endpointName, apiUrl, retries - 1)
      case Failure(connectionException) =>
        _logger.error(s"Ingestion error, no retries, dropping snapshot... ${connectionException.getMessage}")
    }

  }

  private def createHttpClient(config: Settings): OkHttpClient = {
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
    Thread.sleep(settings.clientBackoff.toMillis)
  }
}
