package kamon.metric

import kamon.tag.TagSet

/**
  * Describes the tagging operations that can be performed on an metric or instrument instance. The tags on the
  * returned instrument are the result of combining any existent tags on the instrument with the newly provided tags,
  * overriding previous tags if needed.
  *
  * Since metrics do not have tags themselves (only instruments do), the returned instruments of tagging operations on
  * metrics only have the provided tags.
  */
trait Tagging[I] {

  /**
    * Returns an instrument with one additional tag defined by the provided key and value pair.
    */
  def withTag(key: String, value: String): I

  /**
    * Returns an instrument with one additional tag defined by the provided key and value pair.
    */
  def withTag(key: String, value: Boolean): I

  /**
    * Returns an instrument with one additional tag defined by the provided key and value pair.
    */
  def withTag(key: String, value: Long): I

  /**
    * Returns an instrument with additional tags from the provided TagSet.
    */
  def withTags(tags: TagSet): I
}