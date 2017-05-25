package kamon
package metric

import java.util.regex.Pattern
import com.typesafe.config.Config

object Filter {
  def fromConfig(config: Config): Filter = {
    val filtersConfig = config.getConfig("kamon.metric.filters")
    val acceptUnmatched = filtersConfig.getBoolean("accept-unmatched")

    val perMetricFilter = filtersConfig.firstLevelKeys.filter(_ != "accept-unmatched") map { metricName: String ⇒
      val includes = readFilters(filtersConfig, s"$metricName.includes")
      val excludes = readFilters(filtersConfig, s"$metricName.excludes")

      (metricName, new IncludeExcludeNameFilter(includes, excludes))
    } toMap

    new Filter(perMetricFilter, acceptUnmatched)
  }

  private def readFilters(filtersConfig: Config, name: String): Seq[NameFilter] = {
    import scala.collection.JavaConverters._
    if(filtersConfig.hasPath(name))
      filtersConfig.getStringList(name).asScala.map(readNameFilter)
    else
      Seq.empty
  }

  private def readNameFilter(pattern: String): NameFilter = {
    if(pattern.startsWith("regex:"))
      new RegexNameFilter(pattern.drop(6))
    else if(pattern.startsWith("glob:"))
      new GlobPathFilter(pattern.drop(5))
    else
      new GlobPathFilter(pattern)
  }
}

class Filter(perMetricFilter: Map[String, NameFilter], acceptUnmatched: Boolean) {
  def accept(metricName: String, pattern: String): Boolean =
    perMetricFilter
      .get(metricName)
      .map(_.accept(pattern))
      .getOrElse(acceptUnmatched)
}

trait NameFilter {
  def accept(name: String): Boolean
}

class IncludeExcludeNameFilter(includes: Seq[NameFilter], excludes: Seq[NameFilter]) extends NameFilter {
  override def accept(name: String): Boolean =
    includes.exists(_.accept(name)) && !excludes.exists(_.accept(name))
}

class RegexNameFilter(pattern: String) extends NameFilter {
  private val pathRegex = pattern.r

  override def accept(name: String): Boolean = name match {
    case pathRegex(_*) ⇒ true
    case _             ⇒ false
  }
}

class GlobPathFilter(glob: String) extends NameFilter {
  private val globPattern = Pattern.compile("(\\*\\*?)|(\\?)|(\\\\.)|(/+)|([^*?]+)")
  private val compiledPattern = getGlobPattern(glob)

  override def accept(name: String): Boolean =
    compiledPattern.matcher(name).matches()

  private def getGlobPattern(glob: String) = {
    val patternBuilder = new StringBuilder
    val matcher = globPattern.matcher(glob)
    while (matcher.find()) {
      val (grp1, grp2, grp3, grp4) = (matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4))
      if (grp1 != null) {
        // match a * or **
        if (grp1.length == 2) {
          // it's a *workers are able to process multiple metrics*
          patternBuilder.append(".*")
        }
        else {
          // it's a *
          patternBuilder.append("[^/]*")
        }
      }
      else if (grp2 != null) {
        // match a '?' glob pattern; any non-slash character
        patternBuilder.append("[^/]")
      }
      else if (grp3 != null) {
        // backslash-escaped value
        patternBuilder.append(Pattern.quote(grp3.substring(1)))
      }
      else if (grp4 != null) {
        // match any number of / chars
        patternBuilder.append("/+")
      }
      else {
        // some other string
        patternBuilder.append(Pattern.quote(matcher.group))
      }
    }

    Pattern.compile(patternBuilder.toString)
  }
}

