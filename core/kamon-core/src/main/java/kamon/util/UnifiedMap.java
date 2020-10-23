/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
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

package kamon.util;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiConsumer;

/**
 *
 * This is a stripped down version of UnifiedMap in Eclipse Collections [1]. Even though in general, Eclipse Collections
 * are pretty neat, we only needed the UnifiedMap, not the entire 10+ MB of jar files that come with it and sadly, we
 * could not find an efficient way to shade the dependency so ended up copying the necessary parts of it.
 *
 * UnifiedMap stores key/value pairs in a single array, where alternate slots are keys and values.  This is nicer to CPU caches as
 * consecutive memory addresses are very cheap to access.  Entry objects are not stored in the table like in java.util.HashMap.
 * Instead of trying to deal with collisions in the main array using Entry objects, we put a special object in
 * the key slot and put a regular Object[] in the value slot. The array contains the key value pairs in consecutive slots,
 * just like the main array, but it's a linear list with no hashing.
 * <p>
 * The final result is a Map implementation that's leaner than java.util.HashMap and faster than Trove's THashMap.
 * The best of both approaches unified together, and thus the name UnifiedMap.
 *
 * [1] https://github.com/eclipse/eclipse-collections
 */

@SuppressWarnings("ObjectEquality")
public class UnifiedMap<K, V> implements Map<K, V>
{
  protected static final Object NULL_KEY = new Object()
  {
    @Override
    public boolean equals(Object obj)
    {
      throw new RuntimeException("Possible corruption through unsynchronized concurrent modification.");
    }

    @Override
    public int hashCode()
    {
      throw new RuntimeException("Possible corruption through unsynchronized concurrent modification.");
    }

    @Override
    public String toString()
    {
      return "UnifiedMap.NULL_KEY";
    }
  };

  protected static final Object CHAINED_KEY = new Object()
  {
    @Override
    public boolean equals(Object obj)
    {
      throw new RuntimeException("Possible corruption through unsynchronized concurrent modification.");
    }

    @Override
    public int hashCode()
    {
      throw new RuntimeException("Possible corruption through unsynchronized concurrent modification.");
    }

    @Override
    public String toString()
    {
      return "UnifiedMap.CHAINED_KEY";
    }
  };

  protected static final float DEFAULT_LOAD_FACTOR = 0.75f;

  protected static final int DEFAULT_INITIAL_CAPACITY = 8;

  private static final long serialVersionUID = 1L;

  protected transient Object[] table;

  protected transient int occupied;

  protected float loadFactor = DEFAULT_LOAD_FACTOR;

  protected int maxSize;

  public UnifiedMap()
  {
    this.allocate(DEFAULT_INITIAL_CAPACITY << 1);
  }

  public UnifiedMap(int initialCapacity)
  {
    this(initialCapacity, DEFAULT_LOAD_FACTOR);
  }

  public UnifiedMap(int initialCapacity, float loadFactor)
  {
    if (initialCapacity < 0)
    {
      throw new IllegalArgumentException("initial capacity cannot be less than 0");
    }
    if (loadFactor <= 0.0)
    {
      throw new IllegalArgumentException("load factor cannot be less than or equal to 0");
    }
    if (loadFactor > 1.0)
    {
      throw new IllegalArgumentException("load factor cannot be greater than 1");
    }

    this.loadFactor = loadFactor;
    this.init(this.fastCeil(initialCapacity / loadFactor));
  }

  public UnifiedMap(Map<? extends K, ? extends V> map)
  {
    this(Math.max(map.size(), DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);

    this.putAll(map);
  }


  public static <K, V> UnifiedMap<K, V> newMap()
  {
    return new UnifiedMap<>();
  }

  public static <K, V> UnifiedMap<K, V> newMap(int size)
  {
    return new UnifiedMap<>(size);
  }

  public static <K, V> UnifiedMap<K, V> newMap(int size, float loadFactor)
  {
    return new UnifiedMap<>(size, loadFactor);
  }

  public static <K, V> UnifiedMap<K, V> newMap(Map<? extends K, ? extends V> map)
  {
    return new UnifiedMap<>(map);
  }

  public UnifiedMap<K, V> newEmpty(int capacity)
  {
    return new UnifiedMap<>(capacity, this.loadFactor);
  }

  public static <K, V> UnifiedMap<K, V> newWithKeysValues(K key, V value)
  {
    return new UnifiedMap<K, V>(2).withKeysValues(key, value);
  }

  public static <K, V> UnifiedMap<K, V> newWithKeysValues(K key1, V value1, K key2, V value2)
  {
    return new UnifiedMap<K, V>(2).withKeysValues(key1, value1, key2, value2);
  }

  public UnifiedMap<K, V> withKeysValues(K key, V value)
  {
    this.put(key, value);
    return this;
  }

  public UnifiedMap<K, V> withKeysValues(K key1, V value1, K key2, V value2)
  {
    this.put(key1, value1);
    this.put(key2, value2);
    return this;
  }


  private int fastCeil(float v)
  {
    int possibleResult = (int) v;
    if (v - possibleResult > 0.0F)
    {
      possibleResult++;
    }
    return possibleResult;
  }

  protected int init(int initialCapacity)
  {
    int capacity = 1;
    while (capacity < initialCapacity)
    {
      capacity <<= 1;
    }

    return this.allocate(capacity);
  }

  protected int allocate(int capacity)
  {
    this.allocateTable(capacity << 1); // the table size is twice the capacity to handle both keys and values
    this.computeMaxSize(capacity);

    return capacity;
  }

  protected void allocateTable(int sizeToAllocate)
  {
    this.table = new Object[sizeToAllocate];
  }

  protected void computeMaxSize(int capacity)
  {
    this.maxSize = Math.min(capacity - 1, (int) (capacity * this.loadFactor));
  }

  protected int index(Object key)
  {
    // This function ensures that hashCodes that differ only by
    // constant multiples at each bit position have a bounded
    // number of collisions (approximately 8 at default load factor).
    int h = key == null ? 0 : key.hashCode();
    h ^= h >>> 20 ^ h >>> 12;
    h ^= h >>> 7 ^ h >>> 4;
    return (h & (this.table.length >> 1) - 1) << 1;
  }

  @Override
  public void clear()
  {
    if (this.occupied == 0)
    {
      return;
    }
    this.occupied = 0;
    Object[] set = this.table;

    for (int i = set.length; i-- > 0; )
    {
      set[i] = null;
    }
  }

  @Override
  public V put(K key, V value)
  {
    int index = this.index(key);
    Object cur = this.table[index];
    if (cur == null)
    {
      this.table[index] = UnifiedMap.toSentinelIfNull(key);
      this.table[index + 1] = value;
      if (++this.occupied > this.maxSize)
      {
        this.rehash(this.table.length);
      }
      return null;
    }
    if (cur != CHAINED_KEY && this.nonNullTableObjectEquals(cur, key))
    {
      V result = (V) this.table[index + 1];
      this.table[index + 1] = value;
      return result;
    }
    return this.chainedPut(key, index, value);
  }

  private V chainedPut(K key, int index, V value)
  {
    if (this.table[index] == CHAINED_KEY)
    {
      Object[] chain = (Object[]) this.table[index + 1];
      for (int i = 0; i < chain.length; i += 2)
      {
        if (chain[i] == null)
        {
          chain[i] = UnifiedMap.toSentinelIfNull(key);
          chain[i + 1] = value;
          if (++this.occupied > this.maxSize)
          {
            this.rehash(this.table.length);
          }
          return null;
        }
        if (this.nonNullTableObjectEquals(chain[i], key))
        {
          V result = (V) chain[i + 1];
          chain[i + 1] = value;
          return result;
        }
      }
      Object[] newChain = new Object[chain.length + 4];
      System.arraycopy(chain, 0, newChain, 0, chain.length);
      this.table[index + 1] = newChain;
      newChain[chain.length] = UnifiedMap.toSentinelIfNull(key);
      newChain[chain.length + 1] = value;
      if (++this.occupied > this.maxSize)
      {
        this.rehash(this.table.length);
      }
      return null;
    }
    Object[] newChain = new Object[4];
    newChain[0] = this.table[index];
    newChain[1] = this.table[index + 1];
    newChain[2] = UnifiedMap.toSentinelIfNull(key);
    newChain[3] = value;
    this.table[index] = CHAINED_KEY;
    this.table[index + 1] = newChain;
    if (++this.occupied > this.maxSize)
    {
      this.rehash(this.table.length);
    }
    return null;
  }

  protected void rehash(int newCapacity)
  {
    int oldLength = this.table.length;
    Object[] old = this.table;
    this.allocate(newCapacity);
    this.occupied = 0;

    for (int i = 0; i < oldLength; i += 2)
    {
      Object cur = old[i];
      if (cur == CHAINED_KEY)
      {
        Object[] chain = (Object[]) old[i + 1];
        for (int j = 0; j < chain.length; j += 2)
        {
          if (chain[j] != null)
          {
            this.put(this.nonSentinel(chain[j]), (V) chain[j + 1]);
          }
        }
      }
      else if (cur != null)
      {
        this.put(this.nonSentinel(cur), (V) old[i + 1]);
      }
    }
  }

  @Override
  public V get(Object key)
  {
    int index = this.index(key);
    Object cur = this.table[index];
    if (cur != null)
    {
      Object val = this.table[index + 1];
      if (cur == CHAINED_KEY)
      {
        return this.getFromChain((Object[]) val, (K) key);
      }
      if (this.nonNullTableObjectEquals(cur, (K) key))
      {
        return (V) val;
      }
    }
    return null;
  }

  private V getFromChain(Object[] chain, K key)
  {
    for (int i = 0; i < chain.length; i += 2)
    {
      Object k = chain[i];
      if (k == null)
      {
        return null;
      }
      if (this.nonNullTableObjectEquals(k, key))
      {
        return (V) chain[i + 1];
      }
    }
    return null;
  }

  @Override
  public boolean containsKey(Object key)
  {
    int index = this.index(key);
    Object cur = this.table[index];
    if (cur == null)
    {
      return false;
    }
    if (cur != CHAINED_KEY && this.nonNullTableObjectEquals(cur, (K) key))
    {
      return true;
    }
    return cur == CHAINED_KEY && this.chainContainsKey((Object[]) this.table[index + 1], (K) key);
  }

  private boolean chainContainsKey(Object[] chain, K key)
  {
    for (int i = 0; i < chain.length; i += 2)
    {
      Object k = chain[i];
      if (k == null)
      {
        return false;
      }
      if (this.nonNullTableObjectEquals(k, key))
      {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean containsValue(Object value)
  {
    for (int i = 0; i < this.table.length; i += 2)
    {
      if (this.table[i] == CHAINED_KEY)
      {
        if (this.chainedContainsValue((Object[]) this.table[i + 1], (V) value))
        {
          return true;
        }
      }
      else if (this.table[i] != null)
      {
        if (UnifiedMap.nullSafeEquals(value, this.table[i + 1]))
        {
          return true;
        }
      }
    }
    return false;
  }

  private boolean chainedContainsValue(Object[] chain, V value)
  {
    for (int i = 0; i < chain.length; i += 2)
    {
      if (chain[i] == null)
      {
        return false;
      }
      if (UnifiedMap.nullSafeEquals(value, chain[i + 1]))
      {
        return true;
      }
    }
    return false;
  }

  public void forEachKeyValue(BiConsumer<? super K, ? super V> consumer)
  {
    for (int i = 0; i < this.table.length; i += 2)
    {
      Object cur = this.table[i];
      if (cur == CHAINED_KEY)
      {
        this.chainedForEachEntry((Object[]) this.table[i + 1], consumer);
      }
      else if (cur != null)
      {
        consumer.accept(this.nonSentinel(cur), (V) this.table[i + 1]);
      }
    }
  }

  public V getFirst()
  {
    for (int i = 0; i < this.table.length; i += 2)
    {
      Object cur = this.table[i];
      if (cur == CHAINED_KEY)
      {
        Object[] chain = (Object[]) this.table[i + 1];
        return (V) chain[1];
      }
      if (cur != null)
      {
        return (V) this.table[i + 1];
      }
    }
    return null;
  }

  private void chainedForEachEntry(Object[] chain, BiConsumer<? super K, ? super V> consumer)
  {
    for (int i = 0; i < chain.length; i += 2)
    {
      Object cur = chain[i];
      if (cur == null)
      {
        return;
      }
      consumer.accept(this.nonSentinel(cur), (V) chain[i + 1]);
    }
  }



//  public void forEachKey(Procedure<? super K> procedure)
//  {
//    for (int i = 0; i < this.table.length; i += 2)
//    {
//      Object cur = this.table[i];
//      if (cur == CHAINED_KEY)
//      {
//        this.chainedForEachKey((Object[]) this.table[i + 1], procedure);
//      }
//      else if (cur != null)
//      {
//        procedure.value(this.nonSentinel(cur));
//      }
//    }
//  }


  @Override
  public boolean isEmpty()
  {
    return this.occupied == 0;
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map)
  {
    if (map instanceof UnifiedMap<?, ?>)
    {
      this.copyMap((UnifiedMap<K, V>) map);
    }
    else
    {
      Iterator<? extends Entry<? extends K, ? extends V>> iterator = this.getEntrySetFrom(map).iterator();
      while (iterator.hasNext())
      {
        Entry<? extends K, ? extends V> entry = iterator.next();
        this.put(entry.getKey(), entry.getValue());
      }
    }
  }

  private Set<? extends Map.Entry<? extends K, ? extends V>> getEntrySetFrom(Map<? extends K, ? extends V> map)
  {
    Set<? extends Map.Entry<? extends K, ? extends V>> entries = map.entrySet();
    if (entries != null)
    {
      return entries;
    }
    if (map.isEmpty())
    {
      return Collections.EMPTY_SET;
    }
    throw new IllegalStateException("Entry set was null and size was non-zero");
  }

  protected void copyMap(UnifiedMap<K, V> unifiedMap)
  {
    for (int i = 0; i < unifiedMap.table.length; i += 2)
    {
      Object cur = unifiedMap.table[i];
      if (cur == CHAINED_KEY)
      {
        this.copyChain((Object[]) unifiedMap.table[i + 1]);
      }
      else if (cur != null)
      {
        this.put(this.nonSentinel(cur), (V) unifiedMap.table[i + 1]);
      }
    }
  }

  private void copyChain(Object[] chain)
  {
    for (int j = 0; j < chain.length; j += 2)
    {
      Object cur = chain[j];
      if (cur == null)
      {
        break;
      }
      this.put(this.nonSentinel(cur), (V) chain[j + 1]);
    }
  }

  @Override
  public V remove(Object key)
  {
    int index = this.index(key);
    Object cur = this.table[index];
    if (cur != null)
    {
      Object val = this.table[index + 1];
      if (cur == CHAINED_KEY)
      {
        return this.removeFromChain((Object[]) val, (K) key, index);
      }
      if (this.nonNullTableObjectEquals(cur, (K) key))
      {
        this.table[index] = null;
        this.table[index + 1] = null;
        this.occupied--;
        return (V) val;
      }
    }
    return null;
  }

  private V removeFromChain(Object[] chain, K key, int index)
  {
    for (int i = 0; i < chain.length; i += 2)
    {
      Object k = chain[i];
      if (k == null)
      {
        return null;
      }
      if (this.nonNullTableObjectEquals(k, key))
      {
        V val = (V) chain[i + 1];
        this.overwriteWithLastElementFromChain(chain, index, i);
        return val;
      }
    }
    return null;
  }

  private void overwriteWithLastElementFromChain(Object[] chain, int index, int i)
  {
    int j = chain.length - 2;
    for (; j > i; j -= 2)
    {
      if (chain[j] != null)
      {
        chain[i] = chain[j];
        chain[i + 1] = chain[j + 1];
        break;
      }
    }
    chain[j] = null;
    chain[j + 1] = null;
    if (j == 0)
    {
      this.table[index] = null;
      this.table[index + 1] = null;
    }
    this.occupied--;
  }

  @Override
  public int size()
  {
    return this.occupied;
  }

  @Override
  public Set<Map.Entry<K, V>> entrySet()
  {
    return new EntrySet();
  }

  @Override
  public Set<K> keySet()
  {
    return new KeySet();
  }

  @Override
  public Collection<V> values()
  {
    return new ValuesCollection();
  }

  @Override
  public boolean equals(Object object)
  {
    if (this == object)
    {
      return true;
    }

    if (!(object instanceof Map))
    {
      return false;
    }

    Map<?, ?> other = (Map<?, ?>) object;
    if (this.size() != other.size())
    {
      return false;
    }

    for (int i = 0; i < this.table.length; i += 2)
    {
      Object cur = this.table[i];
      if (cur == CHAINED_KEY)
      {
        if (!this.chainedEquals((Object[]) this.table[i + 1], other))
        {
          return false;
        }
      }
      else if (cur != null)
      {
        K key = this.nonSentinel(cur);
        V value = (V) this.table[i + 1];
        Object otherValue = other.get(key);
        if (!UnifiedMap.nullSafeEquals(otherValue, value) || (value == null && otherValue == null && !other.containsKey(key)))
        {
          return false;
        }
      }
    }

    return true;
  }

  private boolean chainedEquals(Object[] chain, Map<?, ?> other)
  {
    for (int i = 0; i < chain.length; i += 2)
    {
      Object cur = chain[i];
      if (cur == null)
      {
        return true;
      }
      K key = this.nonSentinel(cur);
      V value = (V) chain[i + 1];
      Object otherValue = other.get(key);
      if (!UnifiedMap.nullSafeEquals(otherValue, value) || (value == null && otherValue == null && !other.containsKey(key)))
      {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode()
  {
    int hashCode = 0;
    for (int i = 0; i < this.table.length; i += 2)
    {
      Object cur = this.table[i];
      if (cur == CHAINED_KEY)
      {
        hashCode += this.chainedHashCode((Object[]) this.table[i + 1]);
      }
      else if (cur != null)
      {
        Object value = this.table[i + 1];
        hashCode += (cur == NULL_KEY ? 0 : cur.hashCode()) ^ (value == null ? 0 : value.hashCode());
      }
    }
    return hashCode;
  }

  private int chainedHashCode(Object[] chain)
  {
    int hashCode = 0;
    for (int i = 0; i < chain.length; i += 2)
    {
      Object cur = chain[i];
      if (cur == null)
      {
        return hashCode;
      }
      Object value = chain[i + 1];
      hashCode += (cur == NULL_KEY ? 0 : cur.hashCode()) ^ (value == null ? 0 : value.hashCode());
    }
    return hashCode;
  }

  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append('{');

    this.forEachKeyValue(new BiConsumer<K, V>()
    {
      private boolean first = true;

      public void accept(K key, V value)
      {
        if (this.first)
        {
          this.first = false;
        }
        else
        {
          builder.append(", ");
        }

        builder.append(key == UnifiedMap.this ? "(this Map)" : key);
        builder.append('=');
        builder.append(value == UnifiedMap.this ? "(this Map)" : value);
      }
    });

    builder.append('}');
    return builder.toString();
  }


  protected class KeySet implements Set<K>
  {
    private static final long serialVersionUID = 1L;

    @Override
    public boolean add(K key)
    {
      throw new UnsupportedOperationException("Cannot call add() on " + this.getClass().getSimpleName());
    }

    @Override
    public boolean addAll(Collection<? extends K> collection)
    {
      throw new UnsupportedOperationException("Cannot call addAll() on " + this.getClass().getSimpleName());
    }

    @Override
    public void clear()
    {
      UnifiedMap.this.clear();
    }

    @Override
    public boolean contains(Object o)
    {
      return UnifiedMap.this.containsKey(o);
    }

    @Override
    public boolean containsAll(Collection<?> collection)
    {
      for (Object aCollection : collection)
      {
        if (!UnifiedMap.this.containsKey(aCollection))
        {
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean isEmpty()
    {
      return UnifiedMap.this.isEmpty();
    }

    @Override
    public Iterator<K> iterator()
    {
      return new KeySetIterator();
    }

    @Override
    public boolean remove(Object key)
    {
      int oldSize = UnifiedMap.this.occupied;
      UnifiedMap.this.remove(key);
      return UnifiedMap.this.occupied != oldSize;
    }

    @Override
    public boolean removeAll(Collection<?> collection)
    {
      int oldSize = UnifiedMap.this.occupied;
      for (Object object : collection)
      {
        UnifiedMap.this.remove(object);
      }
      return oldSize != UnifiedMap.this.occupied;
    }

    public void putIfFound(Object key, Map<K, V> other)
    {
      int index = UnifiedMap.this.index(key);
      Object cur = UnifiedMap.this.table[index];
      if (cur != null)
      {
        Object val = UnifiedMap.this.table[index + 1];
        if (cur == CHAINED_KEY)
        {
          this.putIfFoundFromChain((Object[]) val, (K) key, other);
          return;
        }
        if (UnifiedMap.this.nonNullTableObjectEquals(cur, (K) key))
        {
          other.put(UnifiedMap.this.nonSentinel(cur), (V) val);
        }
      }
    }

    private void putIfFoundFromChain(Object[] chain, K key, Map<K, V> other)
    {
      for (int i = 0; i < chain.length; i += 2)
      {
        Object k = chain[i];
        if (k == null)
        {
          return;
        }
        if (UnifiedMap.this.nonNullTableObjectEquals(k, key))
        {
          other.put(UnifiedMap.this.nonSentinel(k), (V) chain[i + 1]);
        }
      }
    }

    @Override
    public boolean retainAll(Collection<?> collection)
    {
      int retainedSize = collection.size();
      UnifiedMap<K, V> retainedCopy = UnifiedMap.this.newEmpty(retainedSize);
      for (Object key : collection)
      {
        this.putIfFound(key, retainedCopy);
      }
      if (retainedCopy.size() < this.size())
      {
        UnifiedMap.this.maxSize = retainedCopy.maxSize;
        UnifiedMap.this.occupied = retainedCopy.occupied;
        UnifiedMap.this.table = retainedCopy.table;
        return true;
      }
      return false;
    }

    @Override
    public int size()
    {
      return UnifiedMap.this.size();
    }

    protected void copyKeys(Object[] result)
    {
      Object[] table = UnifiedMap.this.table;
      int count = 0;
      for (int i = 0; i < table.length; i += 2)
      {
        Object x = table[i];
        if (x != null)
        {
          if (x == CHAINED_KEY)
          {
            Object[] chain = (Object[]) table[i + 1];
            for (int j = 0; j < chain.length; j += 2)
            {
              Object cur = chain[j];
              if (cur == null)
              {
                break;
              }
              result[count++] = UnifiedMap.this.nonSentinel(cur);
            }
          }
          else
          {
            result[count++] = UnifiedMap.this.nonSentinel(x);
          }
        }
      }
    }

    @Override
    public boolean equals(Object obj)
    {
      if (obj instanceof Set)
      {
        Set<?> other = (Set<?>) obj;
        if (other.size() == this.size())
        {
          return this.containsAll(other);
        }
      }
      return false;
    }

    @Override
    public int hashCode()
    {
      int hashCode = 0;
      Object[] table = UnifiedMap.this.table;
      for (int i = 0; i < table.length; i += 2)
      {
        Object x = table[i];
        if (x != null)
        {
          if (x == CHAINED_KEY)
          {
            Object[] chain = (Object[]) table[i + 1];
            for (int j = 0; j < chain.length; j += 2)
            {
              Object cur = chain[j];
              if (cur == null)
              {
                break;
              }
              hashCode += cur == NULL_KEY ? 0 : cur.hashCode();
            }
          }
          else
          {
            hashCode += x == NULL_KEY ? 0 : x.hashCode();
          }
        }
      }
      return hashCode;
    }

    @Override
    public Object[] toArray()
    {
      int size = UnifiedMap.this.size();
      Object[] result = new Object[size];
      this.copyKeys(result);
      return result;
    }

    @Override
    public <T> T[] toArray(T[] result)
    {
      int size = UnifiedMap.this.size();
      if (result.length < size)
      {
        result = (T[]) Array.newInstance(result.getClass().getComponentType(), size);
      }
      this.copyKeys(result);
      if (size < result.length)
      {
        result[size] = null;
      }
      return result;
    }
  }

  protected abstract class PositionalIterator<T> implements Iterator<T>
  {
    protected int count;
    protected int position;
    protected int chainPosition;
    protected boolean lastReturned;

    @Override
    public boolean hasNext()
    {
      return this.count < UnifiedMap.this.size();
    }

    @Override
    public void remove()
    {
      if (!this.lastReturned)
      {
        throw new IllegalStateException("next() must be called as many times as remove()");
      }
      this.count--;
      UnifiedMap.this.occupied--;

      if (this.chainPosition != 0)
      {
        this.removeFromChain();
        return;
      }

      int pos = this.position - 2;
      Object cur = UnifiedMap.this.table[pos];
      if (cur == CHAINED_KEY)
      {
        this.removeLastFromChain((Object[]) UnifiedMap.this.table[pos + 1], pos);
        return;
      }
      UnifiedMap.this.table[pos] = null;
      UnifiedMap.this.table[pos + 1] = null;
      this.position = pos;
      this.lastReturned = false;
    }

    protected void removeFromChain()
    {
      Object[] chain = (Object[]) UnifiedMap.this.table[this.position + 1];
      int pos = this.chainPosition - 2;
      int replacePos = this.chainPosition;
      while (replacePos < chain.length - 2 && chain[replacePos + 2] != null)
      {
        replacePos += 2;
      }
      chain[pos] = chain[replacePos];
      chain[pos + 1] = chain[replacePos + 1];
      chain[replacePos] = null;
      chain[replacePos + 1] = null;
      this.chainPosition = pos;
      this.lastReturned = false;
    }

    protected void removeLastFromChain(Object[] chain, int tableIndex)
    {
      int pos = chain.length - 2;
      while (chain[pos] == null)
      {
        pos -= 2;
      }
      if (pos == 0)
      {
        UnifiedMap.this.table[tableIndex] = null;
        UnifiedMap.this.table[tableIndex + 1] = null;
      }
      else
      {
        chain[pos] = null;
        chain[pos + 1] = null;
      }
      this.lastReturned = false;
    }
  }

  protected class KeySetIterator extends PositionalIterator<K>
  {
    protected K nextFromChain()
    {
      Object[] chain = (Object[]) UnifiedMap.this.table[this.position + 1];
      Object cur = chain[this.chainPosition];
      this.chainPosition += 2;
      if (this.chainPosition >= chain.length
          || chain[this.chainPosition] == null)
      {
        this.chainPosition = 0;
        this.position += 2;
      }
      this.lastReturned = true;
      return UnifiedMap.this.nonSentinel(cur);
    }

    @Override
    public K next()
    {
      if (!this.hasNext())
      {
        throw new NoSuchElementException("next() called, but the iterator is exhausted");
      }
      this.count++;
      Object[] table = UnifiedMap.this.table;
      if (this.chainPosition != 0)
      {
        return this.nextFromChain();
      }
      while (table[this.position] == null)
      {
        this.position += 2;
      }
      Object cur = table[this.position];
      if (cur == CHAINED_KEY)
      {
        return this.nextFromChain();
      }
      this.position += 2;
      this.lastReturned = true;
      return UnifiedMap.this.nonSentinel(cur);
    }
  }

  private static boolean nullSafeEquals(Object value, Object other)
  {
    if (value == null)
    {
      if (other == null)
      {
        return true;
      }
    }
    else if (other == value || value.equals(other))
    {
      return true;
    }
    return false;
  }

  protected final class ImmutableEntry implements Map.Entry<K, V> {
      private final K key;
      private final V value;

      ImmutableEntry(K key, V value) {
        this.key = key;
        this.value = value;
      }

      @Override
      public K getKey() {
        return key;
      }

      @Override
      public V getValue() {
        return value;
      }

      @Override
      public V setValue(V value) {
        throw new UnsupportedOperationException("Cannot call setValue() on " + this.getClass().getSimpleName());
      }
    }

  protected class EntrySet implements Set<Map.Entry<K, V>>
  {
    private static final long serialVersionUID = 1L;
    private transient WeakReference<UnifiedMap<K, V>> holder = new WeakReference<>(UnifiedMap.this);

    @Override
    public boolean add(Map.Entry<K, V> entry)
    {
      throw new UnsupportedOperationException("Cannot call add() on " + this.getClass().getSimpleName());
    }

    @Override
    public boolean addAll(Collection<? extends Map.Entry<K, V>> collection)
    {
      throw new UnsupportedOperationException("Cannot call addAll() on " + this.getClass().getSimpleName());
    }

    @Override
    public void clear()
    {
      UnifiedMap.this.clear();
    }

    public boolean containsEntry(Map.Entry<?, ?> entry)
    {
      return this.getEntry(entry) != null;
    }

    private Map.Entry<K, V> getEntry(Map.Entry<?, ?> entry)
    {
      K key = (K) entry.getKey();
      V value = (V) entry.getValue();
      int index = UnifiedMap.this.index(key);

      Object cur = UnifiedMap.this.table[index];
      Object curValue = UnifiedMap.this.table[index + 1];
      if (cur == CHAINED_KEY)
      {
        return this.chainGetEntry((Object[]) curValue, key, value);
      }
      if (cur == null)
      {
        return null;
      }
      if (UnifiedMap.this.nonNullTableObjectEquals(cur, key))
      {
        if (UnifiedMap.nullSafeEquals(value, curValue))
        {
          return new UnifiedMap.ImmutableEntry(UnifiedMap.this.nonSentinel(cur), (V) curValue);
        }
      }
      return null;
    }

    private Map.Entry<K, V> chainGetEntry(Object[] chain, K key, V value)
    {
      for (int i = 0; i < chain.length; i += 2)
      {
        Object cur = chain[i];
        if (cur == null)
        {
          return null;
        }
        if (UnifiedMap.this.nonNullTableObjectEquals(cur, key))
        {
          Object curValue = chain[i + 1];
          if (UnifiedMap.nullSafeEquals(value, curValue))
          {
            return new UnifiedMap.ImmutableEntry(UnifiedMap.this.nonSentinel(cur), (V) curValue);
          }
        }
      }
      return null;
    }

    @Override
    public boolean contains(Object o)
    {
      return o instanceof Entry && this.containsEntry((Entry<?, ?>) o);
    }

    @Override
    public boolean containsAll(Collection<?> collection)
    {
      for (Object obj : collection)
      {
        if (!this.contains(obj))
        {
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean isEmpty()
    {
      return UnifiedMap.this.isEmpty();
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator()
    {
      return new EntrySetIterator(this.holder);
    }

    @Override
    public boolean remove(Object e)
    {
      if (!(e instanceof Entry))
      {
        return false;
      }
      Entry<?, ?> entry = (Entry<?, ?>) e;
      K key = (K) entry.getKey();
      V value = (V) entry.getValue();

      int index = UnifiedMap.this.index(key);

      Object cur = UnifiedMap.this.table[index];
      if (cur != null)
      {
        Object val = UnifiedMap.this.table[index + 1];
        if (cur == CHAINED_KEY)
        {
          return this.removeFromChain((Object[]) val, key, value, index);
        }
        if (UnifiedMap.this.nonNullTableObjectEquals(cur, key) && UnifiedMap.nullSafeEquals(value, val))
        {
          UnifiedMap.this.table[index] = null;
          UnifiedMap.this.table[index + 1] = null;
          UnifiedMap.this.occupied--;
          return true;
        }
      }
      return false;
    }

    private boolean removeFromChain(Object[] chain, K key, V value, int index)
    {
      for (int i = 0; i < chain.length; i += 2)
      {
        Object k = chain[i];
        if (k == null)
        {
          return false;
        }
        if (UnifiedMap.this.nonNullTableObjectEquals(k, key))
        {
          V val = (V) chain[i + 1];
          if (UnifiedMap.nullSafeEquals(val, value))
          {
            UnifiedMap.this.overwriteWithLastElementFromChain(chain, index, i);
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public boolean removeAll(Collection<?> collection)
    {
      boolean changed = false;
      for (Object obj : collection)
      {
        if (this.remove(obj))
        {
          changed = true;
        }
      }
      return changed;
    }

    @Override
    public boolean retainAll(Collection<?> collection)
    {
      int retainedSize = collection.size();
      UnifiedMap<K, V> retainedCopy = UnifiedMap.this.newEmpty(retainedSize);

      for (Object obj : collection)
      {
        if (obj instanceof Entry)
        {
          Entry<?, ?> otherEntry = (Entry<?, ?>) obj;
          Entry<K, V> thisEntry = this.getEntry(otherEntry);
          if (thisEntry != null)
          {
            retainedCopy.put(thisEntry.getKey(), thisEntry.getValue());
          }
        }
      }
      if (retainedCopy.size() < this.size())
      {
        UnifiedMap.this.maxSize = retainedCopy.maxSize;
        UnifiedMap.this.occupied = retainedCopy.occupied;
        UnifiedMap.this.table = retainedCopy.table;
        return true;
      }
      return false;
    }

    @Override
    public int size()
    {
      return UnifiedMap.this.size();
    }

    protected void copyEntries(Object[] result)
    {
      Object[] table = UnifiedMap.this.table;
      int count = 0;
      for (int i = 0; i < table.length; i += 2)
      {
        Object x = table[i];
        if (x != null)
        {
          if (x == CHAINED_KEY)
          {
            Object[] chain = (Object[]) table[i + 1];
            for (int j = 0; j < chain.length; j += 2)
            {
              Object cur = chain[j];
              if (cur == null)
              {
                break;
              }
              result[count++] =
                  new WeakBoundEntry<>(UnifiedMap.this.nonSentinel(cur), (V) chain[j + 1], this.holder);
            }
          }
          else
          {
            result[count++] = new WeakBoundEntry<>(UnifiedMap.this.nonSentinel(x), (V) table[i + 1], this.holder);
          }
        }
      }
    }

    @Override
    public Object[] toArray()
    {
      Object[] result = new Object[UnifiedMap.this.size()];
      this.copyEntries(result);
      return result;
    }

    @Override
    public <T> T[] toArray(T[] result)
    {
      int size = UnifiedMap.this.size();
      if (result.length < size)
      {
        result = (T[]) Array.newInstance(result.getClass().getComponentType(), size);
      }
      this.copyEntries(result);
      if (size < result.length)
      {
        result[size] = null;
      }
      return result;
    }

    @Override
    public boolean equals(Object obj)
    {
      if (obj instanceof Set)
      {
        Set<?> other = (Set<?>) obj;
        if (other.size() == this.size())
        {
          return this.containsAll(other);
        }
      }
      return false;
    }

    @Override
    public int hashCode()
    {
      return UnifiedMap.this.hashCode();
    }
  }

  protected class EntrySetIterator extends PositionalIterator<Map.Entry<K, V>>
  {
    private final WeakReference<UnifiedMap<K, V>> holder;

    protected EntrySetIterator(WeakReference<UnifiedMap<K, V>> holder)
    {
      this.holder = holder;
    }

    protected Map.Entry<K, V> nextFromChain()
    {
      Object[] chain = (Object[]) UnifiedMap.this.table[this.position + 1];
      Object cur = chain[this.chainPosition];
      Object value = chain[this.chainPosition + 1];
      this.chainPosition += 2;
      if (this.chainPosition >= chain.length
          || chain[this.chainPosition] == null)
      {
        this.chainPosition = 0;
        this.position += 2;
      }
      this.lastReturned = true;
      return new WeakBoundEntry<>(UnifiedMap.this.nonSentinel(cur), (V) value, this.holder);
    }

    @Override
    public Map.Entry<K, V> next()
    {
      if (!this.hasNext())
      {
        throw new NoSuchElementException("next() called, but the iterator is exhausted");
      }
      this.count++;
      Object[] table = UnifiedMap.this.table;
      if (this.chainPosition != 0)
      {
        return this.nextFromChain();
      }
      while (table[this.position] == null)
      {
        this.position += 2;
      }
      Object cur = table[this.position];
      Object value = table[this.position + 1];
      if (cur == CHAINED_KEY)
      {
        return this.nextFromChain();
      }
      this.position += 2;
      this.lastReturned = true;
      return new WeakBoundEntry<>(UnifiedMap.this.nonSentinel(cur), (V) value, this.holder);
    }
  }

  protected static class WeakBoundEntry<K, V> implements Map.Entry<K, V>
  {
    protected final K key;
    protected V value;
    protected final WeakReference<UnifiedMap<K, V>> holder;

    protected WeakBoundEntry(K key, V value, WeakReference<UnifiedMap<K, V>> holder)
    {
      this.key = key;
      this.value = value;
      this.holder = holder;
    }

    @Override
    public K getKey()
    {
      return this.key;
    }

    @Override
    public V getValue()
    {
      return this.value;
    }

    @Override
    public V setValue(V value)
    {
      this.value = value;
      UnifiedMap<K, V> map = this.holder.get();
      if (map != null && map.containsKey(this.key))
      {
        return map.put(this.key, value);
      }
      return null;
    }

    @Override
    public boolean equals(Object obj)
    {
      if (obj instanceof Entry)
      {
        Entry<?, ?> other = (Entry<?, ?>) obj;
        K otherKey = (K) other.getKey();
        V otherValue = (V) other.getValue();
        return UnifiedMap.nullSafeEquals(this.key, otherKey)
            && UnifiedMap.nullSafeEquals(this.value, otherValue);
      }
      return false;
    }

    @Override
    public int hashCode()
    {
      return (this.key == null ? 0 : this.key.hashCode())
          ^ (this.value == null ? 0 : this.value.hashCode());
    }

    @Override
    public String toString()
    {
      return this.key + "=" + this.value;
    }
  }

  protected class ValuesCollection implements Collection<V>
  {
    private static final long serialVersionUID = 1L;

    @Override
    public boolean add(V v)
    {
      throw new UnsupportedOperationException("Cannot call add() on " + this.getClass().getSimpleName());
    }

    @Override
    public boolean addAll(Collection<? extends V> collection)
    {
      throw new UnsupportedOperationException("Cannot call addAll() on " + this.getClass().getSimpleName());
    }

    @Override
    public void clear()
    {
      UnifiedMap.this.clear();
    }

    @Override
    public boolean contains(Object o)
    {
      return UnifiedMap.this.containsValue(o);
    }

    @Override
    public boolean containsAll(Collection<?> collection)
    {
      throw new UnsupportedOperationException("Cannot call containsAll on ValuesCollection");
    }

    @Override
    public boolean isEmpty()
    {
      return UnifiedMap.this.isEmpty();
    }

    @Override
    public Iterator<V> iterator()
    {
      return new ValuesIterator();
    }

    @Override
    public boolean remove(Object o)
    {
      // this is so slow that the extra overhead of the iterator won't be noticeable
      if (o == null)
      {
        for (Iterator<V> it = this.iterator(); it.hasNext(); )
        {
          if (it.next() == null)
          {
            it.remove();
            return true;
          }
        }
      }
      else
      {
        for (Iterator<V> it = this.iterator(); it.hasNext(); )
        {
          V o2 = it.next();
          if (o == o2 || o2.equals(o))
          {
            it.remove();
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public boolean removeAll(Collection<?> collection)
    {
      // todo: this is N^2. if c is large, we should copy the values to a set.
      boolean changed = false;

      for (Object obj : collection)
      {
        if (this.remove(obj))
        {
          changed = true;
        }
      }
      return changed;
    }

    @Override
    public boolean retainAll(Collection<?> collection)
    {
      boolean modified = false;
      Iterator<V> e = this.iterator();
      while (e.hasNext())
      {
        if (!collection.contains(e.next()))
        {
          e.remove();
          modified = true;
        }
      }
      return modified;
    }

    @Override
    public int size()
    {
      return UnifiedMap.this.size();
    }

    protected void copyValues(Object[] result)
    {
      int count = 0;
      for (int i = 0; i < UnifiedMap.this.table.length; i += 2)
      {
        Object x = UnifiedMap.this.table[i];
        if (x != null)
        {
          if (x == CHAINED_KEY)
          {
            Object[] chain = (Object[]) UnifiedMap.this.table[i + 1];
            for (int j = 0; j < chain.length; j += 2)
            {
              Object cur = chain[j];
              if (cur == null)
              {
                break;
              }
              result[count++] = chain[j + 1];
            }
          }
          else
          {
            result[count++] = UnifiedMap.this.table[i + 1];
          }
        }
      }
    }

    @Override
    public Object[] toArray()
    {
      int size = UnifiedMap.this.size();
      Object[] result = new Object[size];
      this.copyValues(result);
      return result;
    }

    @Override
    public <T> T[] toArray(T[] result)
    {
      int size = UnifiedMap.this.size();
      if (result.length < size)
      {
        result = (T[]) Array.newInstance(result.getClass().getComponentType(), size);
      }
      this.copyValues(result);
      if (size < result.length)
      {
        result[size] = null;
      }
      return result;
    }
  }

  protected class ValuesIterator extends PositionalIterator<V>
  {
    protected V nextFromChain()
    {
      Object[] chain = (Object[]) UnifiedMap.this.table[this.position + 1];
      V val = (V) chain[this.chainPosition + 1];
      this.chainPosition += 2;
      if (this.chainPosition >= chain.length
          || chain[this.chainPosition] == null)
      {
        this.chainPosition = 0;
        this.position += 2;
      }
      this.lastReturned = true;
      return val;
    }

    @Override
    public V next()
    {
      if (!this.hasNext())
      {
        throw new NoSuchElementException("next() called, but the iterator is exhausted");
      }
      this.count++;
      Object[] table = UnifiedMap.this.table;
      if (this.chainPosition != 0)
      {
        return this.nextFromChain();
      }
      while (table[this.position] == null)
      {
        this.position += 2;
      }
      Object cur = table[this.position];
      Object val = table[this.position + 1];
      if (cur == CHAINED_KEY)
      {
        return this.nextFromChain();
      }
      this.position += 2;
      this.lastReturned = true;
      return (V) val;
    }
  }

  private K nonSentinel(Object key)
  {
    return key == NULL_KEY ? null : (K) key;
  }

  private static Object toSentinelIfNull(Object key)
  {
    if (key == null)
    {
      return NULL_KEY;
    }
    return key;
  }

  private boolean nonNullTableObjectEquals(Object cur, K key)
  {
    return cur == key || (cur == NULL_KEY ? key == null : cur.equals(key));
  }
}
