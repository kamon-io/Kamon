package kamon.tag

import java.lang.{Boolean => JBoolean, Long => JLong, String => JString}

/**
  * Marker trait for allowed Tag implementations. Users are not meant to create implementations of this trait outside
  * of Kamon. Furthermore, users of TagSet might never need to interact with these classes but rather perform lookups
  * using the lookup DSL.
  */
sealed trait Tag {
  def key: JString
}

object Tag {

  /**
    * Represents a String key pointing to a String value.
    */
  trait String extends Tag {
    def value: JString
  }


  /**
    * Represents a String key pointing to a Boolean value.
    */
  trait Boolean extends Tag {
    def value: JBoolean
  }


  /**
    * Represents a String key pointing to a Long value.
    */
  trait Long extends Tag {
    def value: JLong
  }


  /**
    * Returns the value held inside of a Tag instance. This utility function is specially useful when iterating over
    * tags but not caring about the concrete tag type.
    */
  def unwrapValue(tag: Tag): Any = tag match {
    case t: Tag.String  => t.value
    case t: Tag.Boolean => t.value
    case t: Tag.Long    => t.value
  }
}

