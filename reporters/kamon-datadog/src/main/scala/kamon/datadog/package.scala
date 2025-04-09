/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
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

package kamon

import java.nio.charset.StandardCharsets
import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import com.typesafe.config.Config
import kamon.metric.MeasurementUnit
import kamon.metric.MeasurementUnit.{information, time}
import okhttp3._
import org.slf4j.Logger
import org.slf4j.event.Level

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

package object datadog {

  implicit class InstantImprovements(val instant: Instant) {
    def getEpochNano: Long = {
      instant.getEpochSecond() * 1000000000 +
      instant.getNano()
    }
  }

  implicit class LoggerExtras(val logger: Logger) extends AnyVal {
    def logAtLevel(level: Level, msg: String): Unit = {
      level match {
        case Level.TRACE =>
          logger.trace(msg)
        case Level.DEBUG =>
          logger.debug(msg)
        case Level.INFO =>
          logger.info(msg)
        case Level.WARN =>
          logger.warn(msg)
        case Level.ERROR =>
          logger.error(msg)
      }
    }
  }

  private[datadog] case class HttpClient(
    apiUrl: String,
    apiVersion: Option[String],
    apiKey: Option[String],
    usingCompression: Boolean,
    usingAgent: Boolean,
    connectTimeout: Duration,
    readTimeout: Duration,
    writeTimeout: Duration,
    retries: Int,
    initRetryDelay: Duration
  ) {

    private val httpClient: OkHttpClient = createHttpClient()
    private val retryableStatusCodes: Set[Int] = Set(408, 429, 502, 503, 504)

    def this(config: Config, usingAgent: Boolean) = {
      this(
        config.getString("api-url"),
        {
          if (usingAgent) None
          else {
            val v = config.getString("version")
            if (v != "v1" && v != "v2") {
              sys.error(s"Invalid Datadog API version, the possible values are [v1, v2].")
            } else Some(v)
          }
        },
        if (usingAgent) None else Some(config.getString("api-key")),
        if (usingAgent) false else config.getBoolean("compression"),
        usingAgent,
        config.getDuration("connect-timeout"),
        config.getDuration("read-timeout"),
        config.getDuration("write-timeout"),
        config.getInt("retries"),
        config.getDuration("init-retry-delay")
      )
    }

    @tailrec
    private def doRequestWithRetries(request: Request, attempt: Int = 0): Try[Response] = {
      // Try executing the request
      val responseAttempt = Try(httpClient.newCall(request).execute())

      if (attempt >= retries - 1) {
        responseAttempt
      } else {
        responseAttempt match {
          // If the request succeeded but with a retryable HTTP status code.
          case Success(response) if retryableStatusCodes.contains(response.code) =>
            response.close()
            Thread.sleep(initRetryDelay.toMillis * Math.pow(2, attempt).toLong)
            doRequestWithRetries(request, attempt + 1)

          // Either the request succeeded with an HTTP status not included in `retryableStatusCodes`
          // or we have an unknown failure
          case _ =>
            responseAttempt
        }
      }
    }

    def doMethodWithBody(method: String, contentType: String, contentBody: Array[Byte]): Try[String] = {
      val body = RequestBody.create(MediaType.parse(contentType), contentBody)
      val url = if(apiVersion.contains("v1")) {
        apiUrl + apiKey.map(key => "?api_key=" + key).getOrElse("")
      } else {
        apiUrl
      }
      val request = {
        val builder = new Request.Builder().url(url).method(method, body)
        if(apiVersion.contains("v2")) {
          builder.header("DD-API-KEY", apiKey.getOrElse(""))
        }
        builder.build()
      }

      doRequestWithRetries(request) match {
        case Success(response) =>
          val responseBody = response.body().string()
          response.close()
          if (response.isSuccessful) {
            Success(responseBody)
          } else {
            Failure(new Exception(
              s"Failed to ${method} metrics to Datadog with status code [${response.code()}], Body: [${responseBody}]"
            ))
          }
        case Failure(f) if f.getCause != null =>
          Failure(f.getCause)
        case f @ Failure(_) =>
          f.asInstanceOf[Try[String]]
      }
    }

    def doPost(contentType: String, contentBody: Array[Byte]): Try[String] = {
      doMethodWithBody("POST", contentType, contentBody)
    }

    def doPut(contentType: String, contentBody: Array[Byte]): Try[String] = {
      doMethodWithBody("PUT", contentType, contentBody)
    }

    def doJsonPut(contentBody: String): Try[String] = {
      // Datadog Agent does not accept ";charset=UTF-8", using bytes to send Json posts
      doPut("application/json", contentBody.getBytes(StandardCharsets.UTF_8))
    }

    // Apparently okhttp doesn't require explicit closing of the connection
    private def createHttpClient(): OkHttpClient = {
      val builder = new OkHttpClient.Builder()
        .connectTimeout(connectTimeout.toMillis, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeout.toMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(writeTimeout.toMillis, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)

      if (usingCompression) builder.addInterceptor(new DeflateInterceptor).build()
      else builder.build()
    }
  }

  def readTimeUnit(unit: String): MeasurementUnit = unit match {
    case "s"   => time.seconds
    case "ms"  => time.milliseconds
    case "µs"  => time.microseconds
    case "ns"  => time.nanoseconds
    case other => sys.error(s"Invalid time unit setting [$other], the possible values are [s, ms, µs, ns]")
  }

  def readInformationUnit(unit: String): MeasurementUnit = unit match {
    case "b"   => information.bytes
    case "kb"  => information.kilobytes
    case "mb"  => information.megabytes
    case "gb"  => information.gigabytes
    case other => sys.error(s"Invalid time unit setting [$other], the possible values are [b, kb, mb, gb]")
  }

  def readLogLevel(level: String): Level = level match {
    case "trace" => Level.TRACE
    case "debug" => Level.DEBUG
    case "info"  => Level.INFO
    case "warn"  => Level.WARN
    case "error" => Level.ERROR
    case other =>
      sys.error(s"Invalid log level setting [$other], the possible values are [trace, debug, info, warn, error]")
  }
}
