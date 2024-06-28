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
import kamon.Kamon
import kamon.apm.ingestion.v2.IngestionV2.{Goodbye, Hello, MetricsBatch, SpansBatch}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class ApiClient(settings: Settings) {
  private val _logger = LoggerFactory.getLogger(classOf[ApiClient])
  private val _httpClient = createHttpClient(settings)
  private var _lastAttempt: Instant = Instant.EPOCH

  def postIngestion(metricsBatch: MetricsBatch): Unit =
    postWithRetry(metricsBatch.toByteArray, "metrics", settings.metricsRoute, settings.ingestionRetries)

  def postHello(hello: Hello): Unit =
    postWithRetry(hello.toByteArray, "hello", settings.helloRoute, settings.bootRetries)

  def postGoodbye(goodBye: Goodbye): Unit =
    postWithRetry(goodBye.toByteArray, "goodbye", settings.goodbyeRoute, settings.shutdownRetries)

  def postSpans(spanBatch: SpansBatch): Unit =
    postWithRetry(spanBatch.toByteArray, "spans", settings.spansRoute, settings.tracingRetries)

  def stop(): Unit = {
    _httpClient.dispatcher().executorService().shutdown()
    _httpClient.connectionPool().evictAll()
  }

  @tailrec
  private def postWithRetry(body: Array[Byte], endpointName: String, apiUrl: String, retries: Int): Unit = {
    val clock = Kamon.clock()

    val timeSinceLastPost = Duration.between(_lastAttempt, clock.instant())
    if (timeSinceLastPost.compareTo(settings.clientBackoff) < 0) backoff

    val request: () => Response = () => {
      val reqBody = RequestBody.create(MediaType.parse("application/octet-stream"), body)
      val request = new Request.Builder()
        .url(apiUrl)
        .post(reqBody)
        .build()

      _httpClient.newCall(request).execute
    }

    def tryPosting: Try[Response] = Try {
      _lastAttempt = clock.instant()
      request()
    }

    tryPosting match {
      case Success(response) =>
        // The response body must be closed (even if there is no body in the response),
        // otherwise OkHttp leaks connections.
        response.body().close()

        response.code() match {
          case 200 =>
            _logger.trace("Request to the Kamon APM [{}] endpoint succeeded", endpointName)
          case 401 =>
            _logger.trace("Request to the Kamon APM [{}] endpoint failed due to Invalid API Key", endpointName)
          case status if retries > 0 =>
            _logger.warn(
              s"Failed to process request to the Kamon APM [${endpointName}] endpoint with status code [${status}]. " +
              s"Retrying shortly ($retries left)..."
            )

            backoff
            postWithRetry(body, endpointName, apiUrl, retries - 1)
          case status =>
            _logger.warn(
              s"Failed to process request to the Kamon APM [${endpointName}] endpoint with status code [${status}] " +
              "and no retries left. Dropping the request."
            )
        }

      case Failure(connectionException) if retries > 0 =>
        _logger.warn(
          s"Failed to reach the Kamon APM [${endpointName}] endpoint. Retrying shortly ($retries left)...",
          connectionException
        )
        backoff
        postWithRetry(body, endpointName, apiUrl, retries - 1)

      case Failure(connectionException) =>
        _logger.error(
          s"Failed to reach the Kamon APM [${endpointName}] endpoint with no retries left. Dropping the request.",
          connectionException
        )
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
