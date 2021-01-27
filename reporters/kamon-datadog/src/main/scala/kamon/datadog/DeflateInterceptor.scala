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
      val deflateSink = Okio.buffer(new DeflaterSink(sink, new Deflater))
      body.writeTo(deflateSink)
      deflateSink.close
    }
  }
}
