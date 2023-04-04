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
import java.time.{ Duration, Instant }
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import kamon.metric.MeasurementUnit
import kamon.metric.MeasurementUnit.{ information, time }
import okhttp3._

import scala.util.{ Failure, Success, Try }

package object datadog {

  implicit class InstantImprovements(val instant: Instant) {
    def getEpochNano: Long = {
      instant.getEpochSecond() * 1000000000 +
        instant.getNano()
    }
  }

  private[datadog] case class HttpClient(apiUrl: String, apiKey: Option[String], usingCompression: Boolean, usingAgent: Boolean, connectTimeout: Duration,
                                         readTimeout: Duration, writeTimeout: Duration) {

    val httpClient: OkHttpClient = createHttpClient()

    def this(config: Config, usingAgent: Boolean) = {
      this(
        config.getString("api-url"),
        if (usingAgent) None else Some(config.getString("api-key")),
        if (usingAgent) false else config.getBoolean("compression"),
        usingAgent,
        config.getDuration("connect-timeout"),
        config.getDuration("read-timeout"),
        config.getDuration("write-timeout")
      )
    }

    private def doRequest(request: Request): Try[Response] = {
      Try(httpClient.newCall(request).execute())
    }

    def doMethodWithBody(method: String, contentType: String, contentBody: Array[Byte]): Try[String] = {
      val body = RequestBody.create(MediaType.parse(contentType), contentBody)
      val url = apiUrl + apiKey.map(key => "?api_key=" + key).getOrElse("")
      val request = new Request.Builder().url(url).method(method, body).build

      doRequest(request) match {
        case Success(response) =>
          val responseBody = response.body().string()
          response.close()
          if (response.isSuccessful) {
            Success(responseBody)
          } else {
            Failure(new Exception(s"Failed to ${method} metrics to Datadog with status code [${response.code()}], Body: [${responseBody}]"))
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

    def doJsonPost(contentBody: String): Try[String] = {
      // Datadog Agent does not accept ";charset=UTF-8", using bytes to send Json posts
      doPost("application/json", contentBody.getBytes(StandardCharsets.UTF_8))
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
}
