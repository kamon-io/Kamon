/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package tag

import kamon.tag.TagSet.Lookup
import java.util.function.BiConsumer

import kamon.util.UnifiedMap
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
class TagSet private (private val _underlying: UnifiedMap[String, Any]) {
  import TagSet.withPair

  /**
    * Creates a new TagSet instance that includes the provided key/value pair. If the provided key was already associated
    * with another value then the previous value will be discarded and overwritten with the provided one.
    */
  def withTag(key: String, value: java.lang.String): TagSet =
    withPair(this, key, value)

  /**
    * Creates a new TagSet instance that includes the provided key/value pair. If the provided key was already associated
    * with another value then the previous value will be discarded and overwritten with the provided one.
    */
  def withTag(key: String, value: java.lang.Boolean): TagSet =
    withPair(this, key, value)

  /**
    * Creates a new TagSet instance that includes the provided key/value pair. If the provided key was already associated
    * with another value then the previous value will be discarded and overwritten with the provided one.
    */
  def withTag(key: String, value: java.lang.Long): TagSet =
    withPair(this, key, value)

  /**
    * Creates a new TagSet instance that includes all the tags from the provided Tags instance. If any of the tags in this
    * instance are associated to a key present on the provided instance then the previous value will be discarded and
    * overwritten with the provided one.
    */
  def withTags(other: TagSet): TagSet = {
    val mergedMap = new UnifiedMap[String, Any](other._underlying.size() + this._underlying.size())
    mergedMap.putAll(this._underlying)
    mergedMap.putAll(other._underlying)
    new TagSet(mergedMap)
  }

  /**
    * Creates a new TagSet instance without the provided key, if it was present.
    */
  def without(key: String): TagSet = {
    if (_underlying.containsKey(key)) {
      val withoutKey = new UnifiedMap[String, Any](_underlying.size())
      _underlying.forEachKeyValue(new BiConsumer[String, Any] {
        override def accept(t: String, u: Any): Unit =
          if (t != key) withoutKey.put(t, u)
      })

      new TagSet(withoutKey)

    } else this
  }

  /**
    * Returns whether this TagSet instance does not contain any tags.
    */
  def isEmpty(): Boolean =
    _underlying.isEmpty

  /**
    * Returns whether this TagSet instance contains any tags.
    */
  def nonEmpty(): Boolean =
    !_underlying.isEmpty

  /**
    * Executes a tag lookup. The return type of this function will depend on the provided Lookup. Take a look at the
    * built-in lookups on the [[Lookups]] companion object for more information.
    */
  def get[T](lookup: Lookup[T]): T =
    lookup.execute(_storage)

  /**
    * Returns a immutable sequence of tags created from the contained tags internal representation. Calling this method
    * will cause the creation of a new data structure. Unless you really need to have all the tags as immutable
    * instances it is recommended to use the .iterator() function instead.
    *
    * The returned sequence contains immutable values and is safe to share across threads.
    */
  def all(): Seq[Tag] = {
    var tags: List[Tag] = Nil

    _underlying.forEach(new BiConsumer[String, Any] {
      override def accept(key: String, value: Any): Unit = value match {
        case v: String  => tags = new TagSet.immutable.String(key, v) :: tags
        case v: Boolean => tags = new TagSet.immutable.Boolean(key, v) :: tags
        case v: Long    => tags = new TagSet.immutable.Long(key, v) :: tags
      }
    })

    tags
  }

  /**
    * Returns a pairs iterator from this TagSet. All values are transformed using the provided valueTransform before
    * being returned by the iterator.
    */
  def iterator[T](valueTransform: java.util.function.Function[Any, T]): Iterator[Tag.Pair[T]] =
    iterator(any => valueTransform.apply(any))

  /**
    * Returns a pairs iterator from this TagSet. All values are transformed using the provided valueTransform before
    * being returned by the iterator.
    */
  def iterator[T](valueTransform: Any => T): Iterator[Tag.Pair[T]] = new Iterator[Tag.Pair[T]] {
    private val _entriesIterator = _underlying.entrySet().iterator()
    private val _mutablePair = new TagSet.mutable.Pair[T](null, null.asInstanceOf[T])

    override def hasNext: Boolean =
      _entriesIterator.hasNext

    override def next(): Tag.Pair[T] = {
      val pair = _entriesIterator.next()
      _mutablePair.pairKey = pair.getKey
      _mutablePair.pairValue = valueTransform(pair.getValue)
      _mutablePair
    }
  }

  /**
    * Returns an iterator of tags. The underlying iterator reuses the Tag instances to avoid unnecessary intermediate
    * allocations and thus, it is not safe to share across threads. The most common case for tags iterators is on
    * reporters which will need to iterate through all existent tags only to copy their values into a separate data
    * structure that will be sent to the external systems.
    */
  def iterator(): Iterator[Tag] = new Iterator[Tag] {
    private val _entriesIterator = _underlying.entrySet().iterator()
    private var _longTag: TagSet.mutable.Long = null
    private var _stringTag: TagSet.mutable.String = null
    private var _booleanTag: TagSet.mutable.Boolean = null

    override def hasNext: Boolean =
      _entriesIterator.hasNext

    override def next(): Tag = {
      val pair = _entriesIterator.next()
      pair.getValue match {
        case v: String  => stringTag(pair.getKey, v)
        case v: Boolean => booleanTag(pair.getKey, v)
        case v: Long    => longTag(pair.getKey, v)
      }
    }

    private def stringTag(key: String, value: String): Tag.String =
      if (_stringTag == null) {
        _stringTag = new TagSet.mutable.String(key, value)
        _stringTag
      } else _stringTag.updated(key, value)

    private def booleanTag(key: String, value: Boolean): Tag.Boolean =
      if (_booleanTag == null) {
        _booleanTag = new TagSet.mutable.Boolean(key, value)
        _booleanTag
      } else _booleanTag.updated(key, value)

    private def longTag(key: String, value: Long): Tag.Long =
      if (_longTag == null) {
        _longTag = new TagSet.mutable.Long(key, value)
        _longTag
      } else _longTag.updated(key, value)
  }

  override def equals(other: Any): Boolean =
    other != null && other.isInstanceOf[TagSet] && other.asInstanceOf[TagSet]._underlying == _underlying

  override def hashCode(): Int =
    _underlying.hashCode()

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("{")

    var hasTags = false
    val iterator = _underlying.entrySet().iterator()
    while (iterator.hasNext) {
      val pair = iterator.next()
      if (hasTags)
        sb.append(",")

      sb.append(pair.getKey)
        .append("=")
        .append(pair.getValue)

      hasTags = true
    }

    sb.append("}").toString()
  }

  private val _storage = new TagSet.Storage {
    override def get(key: String): Any = _underlying.get(key)
    override def iterator(): Iterator[Tag] = TagSet.this.iterator()
    override def isEmpty(): Boolean = _underlying.isEmpty
  }
}

object TagSet {

  /**
    * Describes a strategy to lookup values from a TagSet instance. Implementations of this interface will be provided
    * with the actual data structure containing the tags and must perform any necessary runtime type checks to ensure
    * that the returned value is in assignable to the expected type T.
    *
    * Several implementation are provided in the Lookup companion object and it is recommended to import and use those
    * definitions when looking up keys from a Tags instance.
    */
  trait Lookup[T] {

    /**
      * Tries to find a value on a TagSet.Storage and returns a representation of it. In some cases the stored object
      * might be returned as-is, in some others it might be transformed or wrapped on Option/Optional to handle missing
      * values. Take a look at the Lookups companion object for examples..
      */
    def execute(storage: TagSet.Storage): T
  }

  /**
    * A temporary structure that accumulates key/value and creates a new TagSet instance from them. It is faster to use
    * a Builder and add tags to it rather than creating TagSet and add each key individually. Builder instances rely on
    * internal mutable state and are not thread safe.
    */
  trait Builder {

    /** Adds a new key/value pair to the builder. */
    def add(key: String, value: String): Builder

    /** Adds a new key/value pair to the builder. */
    def add(key: String, value: Long): Builder

    /** Adds a new key/value pair to the builder. */
    def add(key: String, value: Boolean): Builder

    /** Adds all key/value pairs to the builder. */
    def add(tags: TagSet): Builder

    /** Creates a new TagSet instance that includes all valid key/value pairs added to this builder. */
    def build(): TagSet
  }

  /**
    * Abstracts the actual storage used for a TagSet. This interface resembles a stripped down interface of an immutable
    * map of String to Any, used to expose the underlying structure where tags are stored to Lookups, without leaking
    * the actual implementation.
    */
  trait Storage {

    /**
      * Gets the value associated with the provided key, or null if no value was found. The decision of returning null
      * when the key is not present is a conscious one, backed by the fact that users will never be exposed to this
      * storage interface and they can decide their way of handling missing values by selecting an appropriate lookup.
      */
    def get(key: String): Any

    /**
      * Provides an Iterator that can go through all key/value pairs contained in the Storage instance.
      */
    def iterator(): Iterator[Tag]

    /**
      * Returns true if there are no tags in the storage.
      */
    def isEmpty(): Boolean

  }

  /**
    * A valid instance of tags that doesn't contain any pairs.
    */
  val Empty = new TagSet(UnifiedMap.newMap[String, Any]())

  /**
    * Creates a new Builder instance.
    */
  def builder(): Builder =
    new Builder.ChainedArray

  /**
    * Construct a new TagSet instance with a single key/value pair.
    */
  def of(key: String, value: java.lang.String): TagSet =
    withPair(Empty, key, value)

  /**
    * Construct a new TagSet instance with a single key/value pair.
    */
  def of(key: String, value: java.lang.Boolean): TagSet =
    withPair(Empty, key, value)

  /**
    * Construct a new TagSet instance with a single key/value pair.
    */
  def of(key: String, value: java.lang.Long): TagSet =
    withPair(Empty, key, value)

  /**
    * Constructs a new TagSet instance from a Map. The returned TagSet will only contain the entries that have String,
    * Long or Boolean values from the supplied map, any other entry in the map will be ignored.
    */
  def from(map: Map[String, Any]): TagSet = {
    val unifiedMap = new UnifiedMap[String, Any](map.size)
    map.foreach { pair => if (isValidPair(pair._1, pair._2, checkValueType = true)) unifiedMap.put(pair._1, pair._2) }

    new TagSet(unifiedMap)
  }

  /**
    * Constructs a new TagSet instance from a Map. The returned TagSet will only contain the entries that have String,
    * Long or Boolean values from the supplied map, any other entry in the map will be ignored.
    */
  def from(map: java.util.Map[String, Any]): TagSet = {
    val unifiedMap = new UnifiedMap[String, Any](map.size)
    map.forEach(new BiConsumer[String, Any] {
      override def accept(key: String, value: Any): Unit =
        if (isValidPair(key, value, checkValueType = true)) unifiedMap.put(key, value)
    })

    new TagSet(unifiedMap)
  }

  private val _logger = LoggerFactory.getLogger(classOf[TagSet])

  private def withPair(parent: TagSet, key: String, value: Any): TagSet =
    if (isValidPair(key, value, checkValueType = true)) {
      val mergedMap = new UnifiedMap[String, Any](parent._underlying.size() + 1)
      mergedMap.putAll(parent._underlying)
      mergedMap.put(key, value)
      new TagSet(mergedMap)
    } else
      parent

  private def isValidPair(key: String, value: Any, checkValueType: Boolean): Boolean = {
    val isValidKey = key != null && key.length > 0
    val isValidValue = value != null && (!checkValueType || isAllowedTagValue(value))
    val isValid = isValidKey && isValidValue

    if (!isValid && _logger.isDebugEnabled) {
      if (!isValidKey && !isValidValue)
        _logger.debug(s"Dismissing tag with invalid key [$key] and invalid value [$value]")
      else if (!isValidKey)
        _logger.debug(s"Dismissing tag with invalid key [$key] and value [$value]")
      else
        _logger.debug(s"Dismissing tag with key [$key] and invalid value [$value]")
    }

    isValid
  }

  private def isAllowedTagValue(v: Any): Boolean =
    (v.isInstanceOf[String] || v.isInstanceOf[Boolean] || v.isInstanceOf[Long])

  private object immutable {
    import java.lang.{Boolean => JBoolean, Long => JLong, String => JString}

    case class String(key: JString, value: JString) extends Tag.String
    case class Boolean(key: JString, value: JBoolean) extends Tag.Boolean
    case class Long(key: JString, value: JLong) extends Tag.Long
  }

  private object mutable {
    import java.lang.{Boolean => JBoolean, Long => JLong, String => JString}

    case class String(var key: JString, var value: JString) extends Tag.String with Updateable[JString]
    case class Boolean(var key: JString, var value: JBoolean) extends Tag.Boolean with Updateable[JBoolean]
    case class Long(var key: JString, var value: JLong) extends Tag.Long with Updateable[JLong]

    case class Pair[T](var pairKey: JString, var pairValue: T) extends Tag.Pair[T] {
      override def key: JString = pairKey
      override def value: T = pairValue
    }

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

  object Builder {

    /**
      * Uses a chain of arrays with fixed size to temporarily hold key/value pairs until the TagSet instance is created.
      * This allows for no allocations when adding new tags on the most common cases (less than 8 tags) and a single
      * allocation for every additional 8 key/value pairs added to the Builder.
      */
    class ChainedArray extends Builder {
      private val _firstBlock = newBlock()
      private var _currentBlock: Array[Any] = _firstBlock
      private var _position = 0

      override def add(key: String, value: String): Builder = {
        addPair(key, value)
        this
      }

      override def add(key: String, value: Long): Builder = {
        addPair(key, value)
        this
      }

      override def add(key: String, value: Boolean): Builder = {
        addPair(key, value)
        this
      }

      override def add(tags: TagSet): Builder = {
        if (tags.nonEmpty())
          tags.iterator().foreach(t => addPair(t.key, Tag.unwrapValue(t)))

        this
      }

      override def build(): TagSet = {
        val unifiedMap = new UnifiedMap[String, Any]()
        var position = 0
        var currentBlock = _firstBlock
        var currentKey = currentBlock(position)
        var currentValue = currentBlock(position + 1)

        while (currentKey != null) {
          unifiedMap.put(currentKey.asInstanceOf[String], currentValue)
          position += 2

          if (position == ChainedArray.BlockSize) {
            val nextBlock = currentBlock(currentBlock.length - 1)
            if (nextBlock == null) {
              currentKey = null
              currentValue = null
            } else {
              position = 0
              currentBlock = nextBlock.asInstanceOf[Array[Any]]
              currentKey = currentBlock(position)
              currentValue = currentBlock(position + 1)
            }
          } else {
            currentKey = currentBlock(position)
            currentValue = currentBlock(position + 1)
          }
        }

        new TagSet(unifiedMap)
      }

      private def addPair(key: String, value: Any): Unit = {
        if (isValidPair(key, value, checkValueType = false)) {
          if (_position == ChainedArray.BlockSize) {
            // Adds an extra block at the end of the current one.
            val extraBlock = newBlock()
            _currentBlock.update(_currentBlock.length - 1, extraBlock)
            _currentBlock = extraBlock
            _position = 0
          }

          _currentBlock.update(_position, key)
          _currentBlock.update(_position + 1, value)
          _position += 2
        }
      }

      private def newBlock(): Array[Any] =
        Array.ofDim[Any](ChainedArray.BlockSize + 1)
    }

    private object ChainedArray {
      val EntriesPerBlock = 8
      val BlockSize = EntriesPerBlock * 2
    }
  }
}
