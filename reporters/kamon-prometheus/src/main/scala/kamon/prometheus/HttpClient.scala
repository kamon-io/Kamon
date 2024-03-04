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

package kamon.prometheus

import java.time.Duration
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import okhttp3._

import scala.util.{Failure, Success, Try}

class HttpClient(val apiUrl: String, connectTimeout: Duration, readTimeout: Duration, writeTimeout: Duration) {

  val httpClient: OkHttpClient = createHttpClient()

  def this(config: Config) = {
    this(
      config.getString("api-url"),
      config.getDuration("connect-timeout"),
      config.getDuration("read-timeout"),
      config.getDuration("write-timeout")
    )
  }

  def doPost(contentType: String, contentBody: Array[Byte]): Try[String] = {
    doMethodWithBody("POST", contentType, contentBody)
  }

  private def doMethodWithBody(method: String, contentType: String, contentBody: Array[Byte]): Try[String] = {
    val body = RequestBody.create(MediaType.parse(contentType), contentBody)
    val request = new Request.Builder().url(apiUrl).method(method, body).build

    doRequest(request) match {
      case Success(response) =>
        val responseBody = response.body().string()
        response.close()
        if (response.isSuccessful) {
          Success(responseBody)
        } else {
          Failure(
            new Exception(s"Failed to $method metrics to Prometheus Pushgateway with status code [${response.code()}], "
              + s"Body: [$responseBody]")
          )
        }
      case Failure(f) if f.getCause != null =>
        Failure(f.getCause)
      case f @ Failure(_) =>
        f.asInstanceOf[Try[String]]
    }
  }

  private def doRequest(request: Request): Try[Response] = {
    Try(httpClient.newCall(request).execute())
  }

  private def createHttpClient(): OkHttpClient = {
    new OkHttpClient.Builder()
      .connectTimeout(connectTimeout.toMillis, TimeUnit.MILLISECONDS)
      .readTimeout(readTimeout.toMillis, TimeUnit.MILLISECONDS)
      .writeTimeout(writeTimeout.toMillis, TimeUnit.MILLISECONDS)
      .retryOnConnectionFailure(false)
      .build()
  }
}
