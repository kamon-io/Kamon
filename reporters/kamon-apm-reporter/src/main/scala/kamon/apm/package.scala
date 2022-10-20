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

package kamon

import java.time.Duration
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import java.net.Proxy
import java.util.regex.Pattern

package object apm {
  private[apm] val _logger = LoggerFactory.getLogger("kamon.apm")
  private val _apiKeyPattern = Pattern.compile("^[a-zA-Z0-9]*$")

  def readSettings(config: Config, path: String): Settings = {
    val apmConfig = config.getConfig(path)
    val apiKey = apmConfig.getString("api-key")

    if(apiKey.equals("none"))
      _logger.error("No API key defined in the kamon.apm.api-key setting")

    Settings (
      apiKey            = apiKey,
      connectionTimeout = apmConfig.getDuration("client.timeouts.connection"),
      readTimeout       = apmConfig.getDuration("client.timeouts.read"),
      baseUrl           = apmConfig.getString("base-url"),
      bootRetries       = apmConfig.getInt("retries.boot"),
      ingestionRetries  = apmConfig.getInt("retries.ingestion"),
      shutdownRetries   = apmConfig.getInt("retries.shutdown"),
      tracingRetries    = apmConfig.getInt("retries.tracing"),
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
    connectionTimeout: Duration,
    readTimeout: Duration,
    baseUrl: String,
    bootRetries: Int,
    ingestionRetries: Int,
    shutdownRetries: Int,
    tracingRetries: Int,
    clientBackoff: Duration,
    proxy: Option[Proxy.Type],
    proxyHost: String,
    proxyPort: Int
  ) {
    def metricsRoute  = s"$baseUrl/metrics"
    def helloRoute    = s"$baseUrl/hello"
    def goodbyeRoute  = s"$baseUrl/goodbye"
    def spansRoute    = s"$baseUrl/spans"
  }

  /*
   *  Internal HDR Histogram state required to convert index to values and get bucket size information. These values
   *  correspond to a histogram configured to have 2 significant value digits prevision and a smallest discernible value
   *  of 1.
   */
  def countsArrayIndex(value: Long): Int = {

    val SubBucketHalfCountMagnitude = 7
    val SubBucketHalfCount          = 128
    val UnitMagnitude               = 0
    val SubBucketCount              = Math.pow(2, SubBucketHalfCountMagnitude + 1).toInt
    val LeadingZeroCountBase        = 64 - UnitMagnitude - SubBucketHalfCountMagnitude - 1
    val SubBucketMask               = (SubBucketCount.toLong - 1) << UnitMagnitude

    def countsArrayIndex(bucketIndex: Int, subBucketIndex: Int): Int = {
      val bucketBaseIndex = (bucketIndex + 1) << SubBucketHalfCountMagnitude
      val offsetInBucket = subBucketIndex - SubBucketHalfCount
      bucketBaseIndex + offsetInBucket
    }

    def getBucketIndex(value: Long): Int =
      LeadingZeroCountBase - java.lang.Long.numberOfLeadingZeros(value | SubBucketMask)

    def getSubBucketIndex(value: Long, bucketIndex: Long): Int  =
      Math.floor(value / Math.pow(2, (bucketIndex + UnitMagnitude))).toInt

    if (value < 0) throw new ArrayIndexOutOfBoundsException("Histogram recorded value cannot be negative.")
    val bucketIndex = getBucketIndex(value)
    val subBucketIndex = getSubBucketIndex(value, bucketIndex)
    countsArrayIndex(bucketIndex, subBucketIndex)
  }


}
