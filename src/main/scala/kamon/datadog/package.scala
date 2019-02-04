package kamon

import java.nio.charset.StandardCharsets
import java.time.{ Duration, Instant }
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import kamon.metric.MeasurementUnit
import kamon.metric.MeasurementUnit.{ information, time }
import okhttp3._
import play.api.libs.json.JsValue

import scala.util.{ Failure, Success, Try }

package object datadog {

  implicit class InstantImprovements(val instant: Instant) {
    def getEpochNano: Long = {
      instant.getEpochSecond() * 1000000000 +
        instant.getNano()
    }
  }

  private[datadog] case class HttpClient(apiUrl: String, apiKey: String, usingAgent: Boolean, connectTimeout: Duration, readTimeout: Duration, requestTimeout: Duration) {

    val httpClient: OkHttpClient = createHttpClient()

    def this(config: Config) = {
      this(
        config.getString("api-url"),
        config.getString("api-key"),
        config.getBoolean("using-agent"),
        config.getDuration("connect-timeout"),
        config.getDuration("read-timeout"),
        config.getDuration("request-timeout")
      )
    }

    private def doRequest(request: Request): Try[Response] = {
      Try(httpClient.newCall(request).execute())
    }

    def doPost(contentType: String, contentBody: Array[Byte]): Try[String] = {
      val body = RequestBody.create(MediaType.parse(contentType), contentBody)
      val url = usingAgent match {
        case true  => apiUrl
        case false => apiUrl + "?api_key=" + apiKey
      }
      val request = new Request.Builder().url(url).post(body).build
      doRequest(request) match {
        case Success(response) =>
          val responseBody = response.body().string()
          response.close()
          if (response.isSuccessful) {
            Success(responseBody)
          } else {
            Failure(new Exception(s"Failed to POST metrics to Datadog with status code [${response.code()}], Body: [${responseBody}]"))
          }
        case Failure(f) =>
          Failure(f.getCause)
      }
    }

    def doJsonPost(contentBody: JsValue): Try[String] = {
      // Datadog Agent does not accept ";charset=UTF-8", using bytes to send Json posts
      doPost("application/json", contentBody.toString().getBytes(StandardCharsets.UTF_8))
    }

    // Apparently okhttp doesn't require explicit closing of the connection
    private def createHttpClient(): OkHttpClient = {
      new OkHttpClient.Builder()
        .connectTimeout(connectTimeout.toMillis, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeout.toMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(requestTimeout.toMillis, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()
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
