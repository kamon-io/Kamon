package kamon.util

object JavaTags {

  /**
   *  Helper method to transform Java maps into Scala maps. Typically this will be used as a static import from
   *  Java code that wants to use tags, as tags are defined as scala.collection.mutable.Map[String, String] and
   *  creating them directly from Java is quite verbose.
   */
  def tagsFromMap(tags: java.util.Map[String, String]): Map[String, String] = {
    import scala.collection.JavaConversions._
    tags.toMap
  }
}
