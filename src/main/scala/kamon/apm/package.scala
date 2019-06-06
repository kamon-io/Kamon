package kamon

import java.time.Duration

import com.typesafe.config.Config
import _root_.kamino.IngestionV1.Plan
import org.slf4j.LoggerFactory
import java.net.Proxy
import java.util.regex.Pattern

package object apm {
  private[apm] val _logger = LoggerFactory.getLogger("kamon.apm")
  private val _apiKeyPattern = Pattern.compile("^[a-zA-Z0-9]*$")

  def readSettings(apmConfig: Config): Settings = {
    val apiKey = apmConfig.getString("api-key")

    if(apiKey.equals("none"))
      _logger.error("No API key defined in the kamino.api-key setting.")

    Settings (
      apiKey            = apiKey,
      plan              = if(apmConfig.getBoolean("enable-tracing")) Plan.METRIC_TRACING else Plan.METRIC_ONLY,
      connectionTimeout = apmConfig.getDuration("client.timeouts.connection"),
      readTimeout       = apmConfig.getDuration("client.timeouts.read"),
      appVersion        = apmConfig.getString("app-version"),
      ingestionApi      = apmConfig.getString("ingestion-api"),
      bootRetries       = apmConfig.getInt("retries.boot"),
      ingestionRetries  = apmConfig.getInt("retries.ingestion"),
      shutdownRetries   = apmConfig.getInt("retries.shutdown"),
      tracingRetries    = apmConfig.getInt("retries.tracing"),
      retryBackoff      = apmConfig.getDuration("retries.backoff"),
      clientBackoff     = apmConfig.getDuration("client.backoff"),
      proxyHost         = apmConfig.getString("proxy.host"),
      proxyPort         = apmConfig.getInt("proxy.port"),
      proxy             = apmConfig.getString("proxy.type").toLowerCase match {
        case "system" => None
        case "socks"  => Some(Proxy.Type.SOCKS)
        case "https"  => Some(Proxy.Type.HTTP)
      }
    )
  }

  def isAcceptableApiKey(apiKey: String): Boolean =
    apiKey != null && apiKey.length == 26 && _apiKeyPattern.matcher(apiKey).matches()

  case class Settings (
    apiKey: String,
    plan: Plan,
    connectionTimeout: Duration,
    readTimeout: Duration,
    appVersion: String,
    ingestionApi: String,
    bootRetries: Int,
    ingestionRetries: Int,
    shutdownRetries: Int,
    tracingRetries: Int,
    retryBackoff: Duration,
    clientBackoff: Duration,
    proxy: Option[Proxy.Type],
    proxyHost: String,
    proxyPort: Int
  ) {
    def ingestionRoute  = s"$ingestionApi/ingest"
    def bootMark        = s"$ingestionApi/hello"
    def shutdownMark    = s"$ingestionApi/goodbye"
    def tracingRoute    = s"$ingestionApi/tracing/ingest"
  }
}
