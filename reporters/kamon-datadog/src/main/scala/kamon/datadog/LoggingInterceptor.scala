package kamon.datadog

import okhttp3.{Interceptor, Response}
import okhttp3.Interceptor.Chain
import org.slf4j.LoggerFactory


class LoggingInterceptor extends Interceptor {
  private val logger = LoggerFactory.getLogger(classOf[LoggingInterceptor])

  def intercept(chain: Chain): Response = {
    val request = chain.request
    logger.info(s"Sending request ${request.toString} with body content length ${request.body().contentLength()}")
    chain.proceed(request)
  }
}
