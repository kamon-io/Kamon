package kamon.tag

import kamon.tag.TagSet.Lookup

import scala.collection.JavaConverters.asScalaIteratorConverter
import java.lang.{Boolean => JBoolean, Long => JLong, String => JString}

import org.slf4j.LoggerFactory

/**
  * A immutable collection of key/value pairs with specialized support for storing String keys pointing to String, Long
  * and/or Boolean values.
  *
  * Instances of Tags store all pairs in the same data structure, but preserving type information for the stored pairs
  * and providing a simple DSL for accessing those values and expressing type expectations. It is also possible to
  * lookup pairs without prescribing a mechanism for handling missing values. I.e. users of this class can decide
  * whether to receive a null, java.util.Optional, scala.Option or any other value when looking up a pair.
  *
  * TagSet instances can only be created from the builder functions on the TagSet companion object. There are two
  * different options to read the contained pairs from a Tags instance:
  *
  *   1. Using the lookup DSL. You can use the Lookup DSL when you know exactly that you are trying to get out of the
  *      tags instance. The lookup DSL is biased towards String keys since they are by far the most common case. For
  *      example, to get a given tag as an Option[String] and another as an Option[Boolean] the following code should
  *      suffice:
  *
  *      import kamon.tag.Tags.Lookup._
  *      val tags = Tags.from(tagMap)
  *      val name = tags.get(option("name"))
  *      val isSignedIn = tags.get(booleanOption("isSignedIn"))
  *
  *   2. Using the .all() and .iterator variants. This option requires you to test the returned instances to verify
  *      whether they are a Tag.String, Tag.Long or Tag.Boolean instance and act accordingly. Fortunately this
  *      cumbersome operation is rarely necessary on user-facing code.
  *
  */
class TagSet private(private val _tags: Map[String, Any]) {
  import TagSet.withPair

  /**
    * Creates a new TagSet instance that includes the provided key/value pair. If the provided key was already associated
    * with another value then the previous value will be discarded and overwritten with the provided one.
    */
  def withTag(key: String, value: JString): TagSet =
    withPair(this, key, value)


  /**
    * Creates a new TagSet instance that includes the provided key/value pair. If the provided key was already associated
    * with another value then the previous value will be discarded and overwritten with the provided one.
    */
  def withTag(key: String, value: JBoolean): TagSet =
    withPair(this, key, value)


  /**
    * Creates a new TagSet instance that includes the provided key/value pair. If the provided key was already associated
    * with another value then the previous value will be discarded and overwritten with the provided one.
    */
  def withTag(key: String, value: JLong): TagSet =
    withPair(this, key, value)


  /**
    * Creates a new TagSet instance that includes all the tags from the provided Tags instance. If any of the tags in this
    * instance are associated to a key present on the provided instance then the previous value will be discarded and
    * overwritten with the provided one.
    */
  def withTags(other: TagSet): TagSet =
    new TagSet(_tags ++ other._tags)


  /**
    * Creates a new TagSet instance that includes the provided key/value pair. If the provided key was already associated
    * with another value then the previous value will be discarded and overwritten with the provided one.
    */
  def and(key: String, value: JString): TagSet =
    withPair(this, key, value)


  /**
    * Creates a new TagSet instance that includes the provided key/value pair. If the provided key was already associated
    * with another value then the previous value will be discarded and overwritten with the provided one.
    */
  def and(key: String, value: JBoolean): TagSet =
    withPair(this, key, value)


  /**
    * Creates a new TagSet instance that includes the provided key/value pair. If the provided key was already associated
    * with another value then the previous value will be discarded and overwritten with the provided one.
    */
  def and(key: String, value: JLong): TagSet =
    withPair(this, key, value)


  /**
    * Creates a new TagSet instance that includes all the tags from the provided Tags instance. If any of the tags in this
    * instance are associated to a key present on the provided instance then the previous value will be discarded and
    * overwritten with the provided one.
    */
  def and(other: TagSet): TagSet =
    new TagSet(_tags ++ other._tags)


  /**
    * Returns whether this TagSet instance does not contain any tags.
    */
  def isEmpty(): Boolean =
    _tags.isEmpty


  /**
    * Returns whether this TagSet instance contains any tags.
    */
  def nonEmpty(): Boolean =
    _tags.nonEmpty


  /**
    * Executes a tag lookup. The return type of this function will depend on the provided Lookup. Take a look at the
    * built-in lookups on the [[Lookups]] companion object for more information.
    */
  def get[T](lookup: Lookup[T]): T =
    lookup.execute(_tags)


  /**
    * Returns a immutable sequence of tags created from the contained tags internal representation. Calling this method
    * will cause the creation of a new data structure. Unless you really need to have all the tags as immutable
    * instances it is recommended to use the .iterator() function instead.
    *
    * The returned sequence contains immutable values and is safe to share across threads.
    */
  def all(): Seq[Tag] =
    _tags.foldLeft(List.empty[Tag]) {
      case (ts, (key, value)) => value match {
        case v: String  => new immutable.String(key, v) :: ts
        case v: Boolean => new immutable.Boolean(key, v) :: ts
        case v: Long    => new immutable.Long(key, v) :: ts
      }
    }


  /**
    * Returns an iterator of tags. The underlying iterator reuses the Tag instances to avoid unnecessary intermediate
    * allocations and thus, it is not safe to share across threads. The most common case for tags iterators is on
    * reporters which will need to iterate through all existent tags only to copy their values into a separate data
    * structure that will be sent to the external systems.
    */
  def iterator(): Iterator[Tag] = new Iterator[Tag] {
    private val _entriesIterator = _tags.iterator
    private var _longTag: mutable.Long = null
    private var _stringTag: mutable.String = null
    private var _booleanTag: mutable.Boolean = null

    override def hasNext: Boolean =
      _entriesIterator.hasNext

    override def next(): Tag = {
      val (key, value) = _entriesIterator.next()
      value match {
        case v: String  => stringTag(key, v)
        case v: Boolean => booleanTag(key, v)
        case v: Long    => longTag(key, v)
      }
    }

    private def stringTag(key: JString, value: JString): Tag.String =
      if(_stringTag == null) {
        _stringTag = new mutable.String(key, value)
        _stringTag
      } else _stringTag.updated(key, value)

    private def booleanTag(key: JString, value: JBoolean): Tag.Boolean =
      if(_booleanTag == null) {
        _booleanTag = new mutable.Boolean(key, value)
        _booleanTag
      } else _booleanTag.updated(key, value)

    private def longTag(key: JString, value: JLong): Tag.Long =
      if(_longTag == null) {
        _longTag = new mutable.Long(key, value)
        _longTag
      } else _longTag.updated(key, value)
  }


  override def equals(other: Any): Boolean =
    other != null && other.isInstanceOf[TagSet] && other.asInstanceOf[TagSet]._tags == this._tags


  override def toString: JString = {
    val sb = new StringBuilder()
    sb.append("Tags{")

    var hasTags = false
    _tags.foreach { case (k, v) =>
      if(hasTags)
        sb.append(",")

      sb.append(k)
        .append("=")
        .append(v)

      hasTags = true
    }

    sb.append("}").toString()
  }

  private object immutable {
    case class String(key: JString, value: JString) extends Tag.String
    case class Boolean(key: JString, value: JBoolean) extends Tag.Boolean
    case class Long(key: JString, value: JLong) extends Tag.Long
  }

  private object mutable {
    case class String(var key: JString, var value: JString) extends Tag.String with Updateable[JString]
    case class Boolean(var key: JString, var value: JBoolean) extends Tag.Boolean with Updateable[JBoolean]
    case class Long(var key: JString, var value: JLong) extends Tag.Long with Updateable[JLong]

    trait Updateable[T] {
      var key: JString
      var value: T

      def updated(key: JString, value: T): this.type = {
        this.key = key
        this.value = value
        this
      }
    }
  }
}

object TagSet {

  /**
    * A valid instance of tags that doesn't contain any pairs.
    */
  val Empty = new TagSet(Map.empty.withDefaultValue(null))


  /**
    * Construct a new TagSet instance with a single key/value pair.
    */
  def from(key: String, value: JString): TagSet =
    withPair(Empty, key, value)


  /**
    * Construct a new TagSet instance with a single key/value pair.
    */
  def from(key: String, value: JBoolean): TagSet =
    withPair(Empty, key, value)


  /**
    * Construct a new TagSet instance with a single key/value pair.
    */
  def from(key: String, value: JLong): TagSet =
    withPair(Empty, key, value)


  /**
    * Constructs a new TagSet instance from a Map. The returned TagSet will only contain the entries that have String,
    * Long or Boolean values from the supplied map, any other entry in the map will be ignored.
    */
  def from(map: Map[String, Any]): TagSet =
    new TagSet(map.filter { case (k, v) => isValidPair(k, v) } withDefaultValue(null))


  /**
    * Constructs a new TagSet instance from a Map. The returned TagSet will only contain the entries that have String,
    * Long or Boolean values from the supplied map, any other entry in the map will be ignored.
    */
  def from(map: java.util.Map[String, Any]): TagSet = {
    val allowedTags = Map.newBuilder[String, Any]
    map.entrySet()
      .iterator()
      .asScala
      .foreach(e => if(isValidPair(e.getKey, e.getValue)) allowedTags += (e.getKey -> e.getValue))

    new TagSet(allowedTags.result().withDefaultValue(null))
  }


  private val _logger = LoggerFactory.getLogger(classOf[TagSet])

  private def withPair(parent: TagSet, key: String, value: Any): TagSet =
    if(isValidPair(key, value))
      new TagSet(parent._tags.updated(key, value))
    else
      parent

  private def isValidPair(key: String, value: Any): Boolean = {
    val isValidKey = key != null && key.nonEmpty
    val isValidValue = isAllowedTagValue(value)
    val isValid = isValidKey && isValidValue

    if(!isValid && _logger.isDebugEnabled) {
      if(!isValidKey && !isValidValue)
        _logger.debug(s"Dismissing tag with invalid key [$key] and invalid value [$value]")
      else if(!isValidKey)
        _logger.debug(s"Dismissing tag with invalid key [$key] and value [$value]")
      else
        _logger.debug(s"Dismissing tag with key [$key] and invalid value [$value]")
    }

    isValid
  }

  private def isAllowedTagValue(v: Any): Boolean =
    v != null && (v.isInstanceOf[String] || v.isInstanceOf[Boolean] || v.isInstanceOf[Long])


  /**
    * Describes a strategy to lookup values from a TagSet instance. Implementations of this interface will be provided
    * with the actual data structure containing the tags and must perform any necessary runtime type checks to ensure
    * that the returned value is in assignable to the expected type T.
    *
    * Several implementation are provided in the Lookup companion object and it is recommended to import and use those
    * definitions when looking up keys from a Tags instance.
    */
  trait Lookup[T] {
    def execute(storage: Map[String, Any]): T
  }
}