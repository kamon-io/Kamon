package kamon.statsd

import java.lang.management.ManagementFactory

import com.typesafe.config.Config
import kamon.metric.UserMetrics.UserMetricGroup
import kamon.metric.{ MetricIdentity, MetricGroupIdentity }

trait MetricKeyGenerator {
  def generateKey(groupIdentity: MetricGroupIdentity, metricIdentity: MetricIdentity): String
}

class SimpleMetricKeyGenerator(config: Config) extends MetricKeyGenerator {
  type Normalizer = String ⇒ String

  val configSettings = config.getConfig("kamon.statsd.simple-metric-key-generator")
  val application = configSettings.getString("application")
  val includeHostname = configSettings.getBoolean("include-hostname")
  val hostnameOverride = configSettings.getString("hostname-override")
  val normalizer = createNormalizer(configSettings.getString("metric-name-normalization-strategy"))

  val normalizedHostname =
    if (hostnameOverride.equals("none")) normalizer(hostName)
    else normalizer(hostnameOverride)

  val baseName: String =
    if (includeHostname) s"$application.$normalizedHostname"
    else application

  def generateKey(groupIdentity: MetricGroupIdentity, metricIdentity: MetricIdentity): String = {
    val normalizedGroupName = normalizer(groupIdentity.name)
    val key = s"${baseName}.${groupIdentity.category.name}.${normalizedGroupName}"

    if (isUserMetric(groupIdentity)) key
    else s"${key}.${metricIdentity.name}"
  }

  def isUserMetric(groupIdentity: MetricGroupIdentity): Boolean = groupIdentity.isInstanceOf[UserMetricGroup]

  def hostName: String = ManagementFactory.getRuntimeMXBean.getName.split('@')(1)

  def createNormalizer(strategy: String): Normalizer = strategy match {
    case "percent-encode" ⇒ PercentEncoder.encode
    case "normalize"      ⇒ (s: String) ⇒ s.replace(": ", "-").replace(" ", "_").replace("/", "_").replace(".", "_")
  }
}

object PercentEncoder {

  def encode(originalString: String): String = {
    val encodedString = new StringBuilder()

    for (character ← originalString) {
      if (shouldEncode(character)) {
        encodedString.append('%')
        val charHexValue = Integer.toHexString(character).toUpperCase
        if (charHexValue.length < 2)
          encodedString.append('0')

        encodedString.append(charHexValue)

      } else {
        encodedString.append(character)
      }
    }
    encodedString.toString()
  }

  def shouldEncode(ch: Char): Boolean = {
    if (ch > 128 || ch < 0) true
    else " %$&+,./:;=?@<>#%".indexOf(ch) >= 0;
  }
}
