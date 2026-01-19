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

import java.time.Instant
import com.typesafe.config.Config
import kamon.metric.MeasurementUnit
import kamon.metric.MeasurementUnit.{information, time}
import org.slf4j.Logger
import org.slf4j.event.Level

package object datadog {

  implicit class InstantImprovements(val instant: Instant) {
    def getEpochNano: Long = {
      instant.getEpochSecond() * 1000000000 +
      instant.getNano()
    }
  }

  implicit class LoggerExtras(val logger: Logger) extends AnyVal {
    def logAtLevel(level: Level, msg: String): Unit = {
      level match {
        case Level.TRACE =>
          logger.trace(msg)
        case Level.DEBUG =>
          logger.debug(msg)
        case Level.INFO =>
          logger.info(msg)
        case Level.WARN =>
          logger.warn(msg)
        case Level.ERROR =>
          logger.error(msg)
      }
    }
  }

  def buildHttpClient(config: Config): HttpClient = {
    val apiUrl = config.getString("api-url")

    val connectTimeout = config.getDuration("connect-timeout")
    val readTimeout = config.getDuration("read-timeout")
    val writeTimeout = config.getDuration("write-timeout")
    val retries = config.getInt("retries")
    val initialRetryDelay = config.getDuration("init-retry-delay")
    val useCompression = if (config.hasPath("compression")) config.getBoolean("compression") else false

    HttpClient(
      endpoint = apiUrl,
      headers = List.empty,
      usingCompression = useCompression,
      connectTimeout = connectTimeout,
      readTimeout = readTimeout,
      writeTimeout = writeTimeout,
      retries = retries,
      initRetryDelay = initialRetryDelay
    )
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

  def readLogLevel(level: String): Level = level match {
    case "trace" => Level.TRACE
    case "debug" => Level.DEBUG
    case "info"  => Level.INFO
    case "warn"  => Level.WARN
    case "error" => Level.ERROR
    case other =>
      sys.error(s"Invalid log level setting [$other], the possible values are [trace, debug, info, warn, error]")
  }
}
