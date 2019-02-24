package kamon.tag

import java.util.Optional
import java.lang.{Boolean => JBoolean, Long => JLong, String => JString}

import kamon.tag.Tags.Lookup

import scala.reflect.ClassTag

object Lookups {

  /**
    * Finds a String value associated to the provided key and returns it. If the key is not present or the value
    * associated with they is not a String then a null is returned.
    */
  def plain(key: JString) = new Lookup[JString] {
    override def run(storage: Map[JString, Any]): JString =
      findAndTransform(key, storage, _plainString, null)
  }


  /**
    * Finds a String value associated to the provided key and returns it, wrapped in an Option[String]. If the key is
    * not present or the value associated with they is not a String then a None is returned.
    */
  def option(key: JString) = new Lookup[Option[JString]] {
    override def run(storage: Map[JString, Any]): Option[JString] =
      findAndTransform(key, storage, _stringOption, None)
  }


  /**
    * Finds a String value associated to the provided key and returns it, wrapped in an Optional[String]. If the key
    * is not present or the value associated with they is not a String then Optional.empty() is returned.
    */
  def optional(key: JString) = new Lookup[Optional[String]] {
    override def run(storage: Map[String, Any]): Optional[String] =
      findAndTransform(key, storage, _stringOptional, Optional.empty())
  }


  /**
    * Finds the value associated to the provided key and coerces it to a String representation. If the key is not
    * present then "unknown" (as a String) will be returned. If the value associated with the key is not a String then
    * the value of the key will be transformed into a String and returned.
    *
    * This lookup type is guaranteed to return a non-null String representation of value.
    */
  def coerce(key: String) = new Lookup[String] {
    override def run(storage: Map[String, Any]): String = {
      val value = storage(key)
      if(value == null)
        "unknown"
      else
        value.toString
    }
  }


  /**
    * Finds a Boolean value associated to the provided key and returns it. If the key is not present or the value
    * associated with they is not a Boolean then a null is returned.
    */
  def plainBoolean(key: String) = new Lookup[JBoolean] {
    override def run(storage: Map[String, Any]): JBoolean =
      findAndTransform(key, storage, _plainBoolean, null)
  }


  /**
    * Finds a Boolean value associated to the provided key and returns it, wrapped in an Option[Boolean]. If the key
    * is not present or the value associated with they is not a Boolean then a None is returned.
    */
  def booleanOption(key: String) = new Lookup[Option[JBoolean]] {
    override def run(storage: Map[String, Any]): Option[JBoolean] =
      findAndTransform(key, storage, _booleanOption, None)
  }


  /**
    * Finds a Boolean value associated to the provided key and returns it, wrapped in an Optional[Boolean]. If the key
    * is not present or the value associated with they is not a Boolean then Optional.empty() is returned.
    */
  def booleanOptional(key: String) = new Lookup[Optional[JBoolean]] {
    override def run(storage: Map[String, Any]): Optional[JBoolean] =
      findAndTransform(key, storage, _booleanOptional, Optional.empty())
  }


  /**
    * Finds a Long value associated to the provided key and returns it. If the key is not present or the value
    * associated with they is not a Long then a null is returned.
    */
  def plainLong(key: String) = new Lookup[JLong] {
    override def run(storage: Map[String, Any]): JLong =
      findAndTransform(key, storage, _plainLong, null)
  }


  /**
    * Finds a Long value associated to the provided key and returns it, wrapped in an Option[Long]. If the key is
    * not present or the value associated with they is not a Long then a None is returned.
    */
  def longOption(key: String) = new Lookup[Option[JLong]] {
    override def run(storage: Map[String, Any]): Option[JLong] =
      findAndTransform(key, storage, _longOption, None)
  }


  /**
    * Finds a Long value associated to the provided key and returns it, wrapped in an Optional[Long]. If the key
    * is not present or the value associated with they is not a Long then Optional.empty() is returned.
    */
  def longOptional(key: String) = new Lookup[Optional[JLong]] {
    override def run(storage: Map[String, Any]): Optional[JLong] =
      findAndTransform(key, storage, _longOptional, Optional.empty())
  }


  ////////////////////////////////////////////////////////////////
  // Transformation helpers for the lookup DSL                  //
  ////////////////////////////////////////////////////////////////

  private def findAndTransform[T, R](key: String, storage: Map[String, Any], transform: R => T, default: T)
    (implicit ct: ClassTag[R]): T = {

    // This assumes that this code will only be used to lookup values from a Tags instance
    // for which the underlying map always has "null" as the default value.
    val value = storage(key)

    if(value == null || !ct.runtimeClass.isInstance(value))
      default
    else
      transform(value.asInstanceOf[R])
  }

  private val _plainString = (a: JString) => a
  private val _stringOption = (a: JString) => Option(a)
  private val _stringOptional = (a: JString) => Optional.of(a)

  private val _plainLong = (a: JLong) => a
  private val _longOption = (a: JLong) => Option(a)
  private val _longOptional = (a: JLong) => Optional.of(a)

  private val _plainBoolean = (a: JBoolean) => a
  private val _booleanOption = (a: JBoolean) => Option(a)
  private val _booleanOptional = (a: JBoolean) => Optional.of(a)

}
