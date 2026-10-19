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
package context

import kamon.context.Context.Entry
import kamon.tag.TagSet
import kamon.trace.Span
import kamon.util.UnifiedMap

/**
  * An immutable set of information that is tied to the processing of single operation in a service. A Context instance
  * can contain tags and entries.
  *
  * Context tags are built on top of the TagSet abstraction that ships with Kamon and since Kamon knows exactly what
  * types of values can be included in a TagSet it can automatically  serialize and deserialize them when going over
  * HTTP and/or Binary transports.
  *
  * Context entries can contain any arbitrary type specified by the user, but require additional configuration and
  * implementation of entry readers and writers if you need them to go over HTTP and/or Binary transports.
  *
  * Context instances are meant to be constructed by using the builder functions on the Context companion object.
  *
  */
class Context private (private val _underlying: UnifiedMap[String, Any], private val _span: Span, val tags: TagSet) {

  /**
    * Gets an entry from this Context. If the entry is present it's current value is returned, otherwise the empty value
    * from the provided key will be returned.
    */
  def get[T](key: Context.Key[T]): T = {
    if (key == Span.Key)
      _span.asInstanceOf[T]
    else
      _underlying.getOrDefault(key.name, key.emptyValue).asInstanceOf[T]
  }

  /**
    * Executes a lookup on the context tags. The actual return type depends on the provided lookup instance. Take a look
    * at the built-in lookups available on the Lookups companion object.
    */
  def getTag[T](lookup: TagSet.Lookup[T]): T =
    tags.get(lookup)

  /**
    * Creates a new Context instance that includes the provided key and value. If the provided key was already
    * associated with another value then the previous value will be discarded and overwritten with the provided one.
    */
  def withEntry[T](key: Context.Key[T], value: T): Context = {
    if (key == Span.Key)
      new Context(_underlying, value.asInstanceOf[Span], tags)
    else {
      val mergedEntries = new UnifiedMap[String, Any](_underlying.size() + 1)
      mergedEntries.putAll(_underlying)
      mergedEntries.put(key.name, value)

      new Context(mergedEntries, _span, tags)
    }
  }

  /**
    * Creates a new Context without the specified Context entry.
    */
  def withoutEntry[T](key: Context.Key[T]): Context = {
    if (_underlying.containsKey(key.name)) {
      if (key == Span.Key)
        if (_span.isEmpty) this else new Context(_underlying, Span.Empty, tags)
      else {
        val mergedEntries = new UnifiedMap[String, Any](_underlying.size())
        mergedEntries.putAll(_underlying)
        mergedEntries.remove(key.name)

        new Context(mergedEntries, _span, tags)
      }
    } else this
  }

  /**
    * Creates a new Context instance that includes the provided tag key and value. If the provided tag key was already
    * associated with another value then the previous tag value will be discarded and overwritten with the provided one.
    */
  def withTag(key: String, value: String): Context =
    new Context(_underlying, _span, tags.withTag(key, value))

  /**
    * Creates a new Context instance that includes the provided tag key and value. If the provided tag key was already
    * associated with another value then the previous tag value will be discarded and overwritten with the provided one.
    */
  def withTag(key: String, value: Long): Context =
    new Context(_underlying, _span, tags.withTag(key, value))

  /**
    * Creates a new Context instance that includes the provided tag key and value. If the provided tag key was already
    * associated with another value then the previous tag value will be discarded and overwritten with the provided one.
    */
  def withTag(key: String, value: Boolean): Context =
    new Context(_underlying, _span, tags.withTag(key, value))

  /**
    * Creates a new Context instance that includes the provided tags. If any of the tags in this instance are associated
    * to a key present on the provided tags then the previous values will be discarded and overwritten with the provided
    * ones.
    */
  def withTags(tags: TagSet): Context =
    new Context(_underlying, _span, this.tags.withTags(tags))

  /**
    * Returns whether this Context does not have any tags and does not have any entries.
    */
  def isEmpty(): Boolean =
    _underlying.isEmpty && tags.isEmpty && _span.isEmpty

  /**
    * Returns whether this Context has any information, either as tags or entries.
    */
  def nonEmpty(): Boolean =
    !isEmpty()

  /**
    * Returns a loosely typed iterator of all entries in this Context.
    */
  def entries(): Iterator[Context.Entry] = new Iterator[Context.Entry] {
    private val _entriesIterator = _underlying.entrySet().iterator()
    private val _entry = Entry.Mutable(null, null)
    private var _processedSpanEntry = _span == Span.Empty

    override def hasNext: Boolean =
      !_processedSpanEntry || _entriesIterator.hasNext

    override def next(): Context.Entry = {
      if (!_processedSpanEntry) {
        _processedSpanEntry = true
        _entry.key = Span.Key.name
        _entry.value = _span
        _entry
      } else {
        val pair = _entriesIterator.next()
        _entry.key = pair.getKey
        _entry.value = pair.getValue
        _entry
      }
    }
  }

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("Context{Entries{span=")
      .append(_span.toString)

    val iterator = _underlying.entrySet().iterator()
    while (iterator.hasNext) {
      val pair = iterator.next()
      sb.append(",")
        .append(pair.getKey)
        .append("=")
        .append(pair.getValue)
    }

    sb.append("},Tags")
      .append(tags.toString)
      .append("}")
      .toString()
  }
}

object Context {

  private val _emptyEntries = UnifiedMap.newMap[String, Any]()

  /**
    * A Context that doesn't have any entries nor tags.
    */
  val Empty = new Context(_emptyEntries, Span.Empty, TagSet.Empty)

  /**
    * Creates a new Context instance with the provided tags and no entries.
    */
  def of(tags: TagSet): Context =
    new Context(_emptyEntries, Span.Empty, tags)

  /**
    * Creates a new Context instance with one tag.
    */
  def of(tagKey: String, tagValue: String): Context =
    new Context(_emptyEntries, Span.Empty, TagSet.of(tagKey, tagValue))

  /**
    * Creates a new Context instance with one tag.
    */
  def of(tagKey: String, tagValue: Long): Context =
    new Context(_emptyEntries, Span.Empty, TagSet.of(tagKey, tagValue))

  /**
    * Creates a new Context instance with one tag.
    */
  def of(tagKey: String, tagValue: Boolean): Context =
    new Context(_emptyEntries, Span.Empty, TagSet.of(tagKey, tagValue))

  /**
    * Creates a new Context instance with the provided key and no tags.
    */
  def of[T](key: Context.Key[T], value: T): Context = {
    if (key == Span.Key)
      new Context(_emptyEntries, value.asInstanceOf[Span], TagSet.Empty)
    else
      new Context(UnifiedMap.newWithKeysValues(key.name, value), Span.Empty, TagSet.Empty)
  }

  /**
    * Creates a new Context instance with a single entry and the provided tags.
    */
  def of[T](key: Context.Key[T], value: T, tags: TagSet): Context = {
    if (key == Span.Key)
      new Context(_emptyEntries, value.asInstanceOf[Span], tags)
    else
      new Context(UnifiedMap.newWithKeysValues(key.name, value), Span.Empty, tags)
  }

  /**
    * Creates a new Context instance with two entries and no tags.
    */
  def of[T, U](keyOne: Context.Key[T], valueOne: T, keyTwo: Context.Key[U], valueTwo: U): Context = {
    if (keyOne == Span.Key)
      new Context(UnifiedMap.newWithKeysValues(keyTwo.name, valueTwo), valueOne.asInstanceOf[Span], TagSet.Empty)
    else if (keyTwo == Span.Key)
      new Context(UnifiedMap.newWithKeysValues(keyOne.name, valueOne), valueTwo.asInstanceOf[Span], TagSet.Empty)
    else
      new Context(UnifiedMap.newWithKeysValues(keyOne.name, valueOne, keyTwo.name, valueTwo), Span.Empty, TagSet.Empty)
  }

  /**
    * Creates a new Context instance with two entries and the provided tags.
    */
  def of[T, U](keyOne: Context.Key[T], valueOne: T, keyTwo: Context.Key[U], valueTwo: U, tags: TagSet): Context = {
    if (keyOne == Span.Key)
      new Context(UnifiedMap.newWithKeysValues(keyTwo.name, valueTwo), valueOne.asInstanceOf[Span], tags)
    else if (keyTwo == Span.Key)
      new Context(UnifiedMap.newWithKeysValues(keyOne.name, valueOne), valueTwo.asInstanceOf[Span], tags)
    else
      new Context(UnifiedMap.newWithKeysValues(keyOne.name, valueOne, keyTwo.name, valueTwo), Span.Empty, tags)
  }

  /**
    * Creates a new Context.Key instance that can be used to insert and retrieve values from the context entries.
    * Context keys must have a unique name since they will be looked up in transports by their name and the context
    * entries are internally stored using their key name as index.
    */
  def key[T](name: String, emptyValue: T): Context.Key[T] =
    new Context.Key(name, emptyValue)

  /**
    * Encapsulates the type, name and empty value for a context entry. All reads and writes from a context instance
    * must be done using a context key, which will ensure the right type is used on both operations. The key's name
    * is used when configuring mappings and incoming/outgoing/returning codecs for context propagation across channels.
    *
    * If you try to read an entry from a context and such entry is not present, the empty value for the key is returned
    * instead.
    */
  final class Key[T](val name: String, val emptyValue: T) {
    override def hashCode(): Int =
      name.hashCode

    override def equals(that: Any): Boolean =
      that.isInstanceOf[Context.Key[_]] && that.asInstanceOf[Context.Key[_]].name == this.name
  }

  /**
    * An untyped representation of a Context entry.
    */
  trait Entry {
    def key: String
    def value: Any
  }

  object Entry {

    private[context] case class Mutable(var key: String, var value: Any) extends Entry
  }
}
