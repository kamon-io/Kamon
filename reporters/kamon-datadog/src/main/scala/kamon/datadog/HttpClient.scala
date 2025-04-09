package kamon.datadog

import okhttp3.{MediaType, OkHttpClient, Request, RequestBody, Response}

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

private[datadog] case class HttpClient(
                                        endpoint: String,
                                        headers: List[(String, String)],
                                        usingCompression: Boolean,
                                        connectTimeout: Duration,
                                        readTimeout: Duration,
                                        writeTimeout: Duration,
                                        retries: Int,
                                        initRetryDelay: Duration
                                      ) {

  private val retryableStatusCodes: Set[Int] = Set(408, 429, 502, 503, 504)

  private lazy val httpClient: OkHttpClient = {
    // Apparently okhttp doesn't require explicit closing of the connection
    val builder = new OkHttpClient.Builder()
      .connectTimeout(connectTimeout.toMillis, TimeUnit.MILLISECONDS)
      .readTimeout(readTimeout.toMillis, TimeUnit.MILLISECONDS)
      .writeTimeout(writeTimeout.toMillis, TimeUnit.MILLISECONDS)
      .retryOnConnectionFailure(true)

    if (usingCompression) builder.addInterceptor(new DeflateInterceptor).build()
    else builder.build()
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

  private def doMethodWithBody(method: String, contentType: String, contentBody: Array[Byte]): Try[String] = {
    val body = RequestBody.create(contentBody, MediaType.parse(contentType))
    val request = {
      val builder = new Request.Builder().url(endpoint).method(method, body)
      headers.foreach {
        case (name, value) => builder.header(name, value)
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
            s"Failed to $method metrics to Datadog with status code [${response.code()}], Body: [$responseBody]"
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

  def doJsonPut(contentBody: String): Try[String] = {
    // Datadog Agent does not accept ";charset=UTF-8", using bytes to send Json posts
    doMethodWithBody("PUT", "application/json", contentBody.getBytes(StandardCharsets.UTF_8))
  }
}
