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

  private def doRequest(request: Request): Try[Response] =
    Try(httpClient.newCall(request).execute())

  def doMethodWithBody(method: String, contentType: String, contentBody: Array[Byte]): Try[String] = {
    val body = RequestBody.create(MediaType.parse(contentType), contentBody)
    val request = new Request.Builder().url(apiUrl).method(method, body).build

    doRequest(request) match {
      case Success(response) =>
        val responseBody = response.body().string()
        response.close()
        if (response.isSuccessful)
          Success(responseBody)
        else {
          Failure(
            new Exception(
              s"Failed to $method metrics to Prometheus Pushgateway with status code [${response.code()}], "
                + s"Body: [$responseBody]"
            )
          )
        }
      case Failure(f) if f.getCause != null =>
        Failure(f.getCause)
      case f @ Failure(_) =>
        f.asInstanceOf[Try[String]]
    }
  }

  def doPost(contentType: String, contentBody: Array[Byte]): Try[String] =
    doMethodWithBody("POST", contentType, contentBody)

  private def createHttpClient(): OkHttpClient = {
    new OkHttpClient.Builder()
      .connectTimeout(connectTimeout.toMillis, TimeUnit.MILLISECONDS)
      .readTimeout(readTimeout.toMillis, TimeUnit.MILLISECONDS)
      .writeTimeout(writeTimeout.toMillis, TimeUnit.MILLISECONDS)
      .retryOnConnectionFailure(false)
      .build()
  }
}
