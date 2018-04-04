package kamon

import com.typesafe.config.Config
import kamon.metric.MeasurementUnit
import kamon.metric.MeasurementUnit.{information, time}
import scala.collection.JavaConverters._

import scala.util.matching.Regex

package object datadog {

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

  def readTagConfig(config: Config): TagConfig = {
    TagConfig(
      FilterConfig(
        config.getStringList("tag-filters.includes").asScala.map(_.r),
        config.getStringList("tag-filters.excludes").asScala.map(_.r)

      ),
      serviceTagName = config.getString("service-tag-name")
    )

  }

  case class TagConfig(filters: FilterConfig, serviceTagName: String)
  case class FilterConfig(includes: Seq[Regex], excludes: Seq[Regex])

}
