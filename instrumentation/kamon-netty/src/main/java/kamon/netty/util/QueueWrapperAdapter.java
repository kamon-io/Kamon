/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */


package kamon.netty.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

public class QueueWrapperAdapter<E> implements Queue<E> {

    private final Queue<E> underlying;

    public QueueWrapperAdapter(Queue<E> underlying) {
        this.underlying = underlying;
    }

    @Override
    public int size() {
        return underlying.size();
    }

    @Override
    public boolean isEmpty() {
        return underlying.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return underlying.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return underlying.iterator();
    }

    @Override
    public Object[] toArray() {
        return underlying.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return underlying.toArray(a);
    }

    @Override
    public boolean add(E e) {
        return underlying.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return underlying.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return underlying.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return underlying.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return underlying.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return underlying.retainAll(c);
    }

    @Override
    public void clear() {
        underlying.clear();
    }

    @Override
    public boolean offer(E e) {
        return underlying.offer(e);
    }

    @Override
    public E remove() {
        return underlying.remove();
    }

    @Override
    public E poll() {
        return underlying.poll();
    }

    @Override
    public E element() {
        return underlying.element();
    }

    @Override
    public E peek() {
        return underlying.peek();
    }
}
