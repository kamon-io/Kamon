package kamon
package instrumentation

import kamon.metric.Metric
import scala.collection.JavaConverters._

/**
  * Utility class that helps to apply the same tags to several metrics and keep track of all the tagged instruments so
  * that they can later be cleaned up. This becomes specially handy when tracking several metrics that are related to
  * each other and should be created and removed together, for example, when tracking metrics on a thread pool you will
  * want to register several metrics related to one particular thread pool with a number of common tags and then remove
  * all of those metrics once the thread pool is shut down.
  *
  * @param commonTags Tags to be applied to all metrics returned by calls to the tag method.
  */
abstract class MetricGroup(commonTags: Map[String, String]) {
  private var _groupInstruments = List.empty[(Metric[_], Map[String, String])]

  /**
    * Tag a metric using the common tags.
    */
  def tag[T](metric: Metric[T]): T =
    register(metric, commonTags)

  /**
    * Tag a metric using the supplied key/value pair and the common tags.
    */
  def tag[T](metric: Metric[T], key: String, value: String): T =
    register(metric, commonTags ++ Map(key -> value))

  /**
    * Tag a metric using the supplied tuple and the common tags.
    */
  def tag[T](metric: Metric[T], tag: (String, String)): T =
    register(metric, commonTags + tag)

  /**
    * Tag a metric using the supplied tags map and the common tags.
    */
  def tag[T](metric: Metric[T], tags: Map[String, String]): T =
    register(metric, commonTags ++ tags)

  /**
    * Tag a metric using the supplied tags map and the common tags.
    */
  def tag[T](metric: Metric[T], tags: java.util.Map[String, String]): T =
    register(metric, commonTags ++ tags.asScala.toMap)


  private def register[T](metric: Metric[T], tags: Map[String, String]): T = synchronized {
    _groupInstruments = (metric -> tags) :: _groupInstruments
    metric.refine(tags)
  }


  /**
    * Removes all metrics that were tagged by this group.
    */
  def cleanup(): Unit = synchronized {
    _groupInstruments foreach {
      case (metric, tags) => metric.remove(tags)
    }
  }
}