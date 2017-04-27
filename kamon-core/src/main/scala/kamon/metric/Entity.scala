package kamon.metric



case class Entity(name: String, category: String, tags: Map[String, String]) {

  override def toString: String = {
    val tagString = tags.map { case (k, v) => k + "=>" + v }.mkString(",")
    "Entity(name=\"" + name + "\", category=\"" + category + "\", tags=\"" + tagString + "\""
  }
}
