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

package kamon.datadog

import okhttp3.Interceptor.Chain
import okhttp3.{Interceptor, MediaType, RequestBody, Response}
import okio.{BufferedSink, DeflaterSink, Okio}

import java.util.zip.Deflater

class DeflateInterceptor extends Interceptor {

  def intercept(chain: Chain): Response = {
    val originalRequest = chain.request

    Option(originalRequest.body) match {
      case Some(body) if Option(originalRequest.header("Content-Encoding")).isEmpty =>
        val compressedRequest = originalRequest
          .newBuilder
          .header("Content-Encoding", "deflate")
          .method(
            originalRequest.method,
            encodeDeflate(body)
          )
          .build

        chain.proceed(compressedRequest)
      case _ =>
        chain.proceed(originalRequest)
    }
  }

  private def encodeDeflate(body: RequestBody): RequestBody = new RequestBody() {
    def contentType: MediaType = body.contentType

    def writeTo(sink: BufferedSink): Unit = {
      val deflateSink = Okio.buffer(new DeflaterSink(sink, new Deflater()))
      body.writeTo(deflateSink)
      deflateSink.close
    }
  }
}
