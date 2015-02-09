/*
 * =========================================================================================
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

package kamon.annotation.util;

import scala.concurrent.util.Unsafe;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;


public class FastObjectPool<T> {

    private Holder<T>[] objects;

    private volatile int takePointer;
    private int releasePointer;

    private final int mask;
    private final long BASE;
    private final long INDEXSCALE;
    private final long ASHIFT;

    public ReentrantLock lock = new ReentrantLock();
    private ThreadLocal<Holder<T>> localValue = new ThreadLocal<>();

    public FastObjectPool(PoolFactory<T> factory, int size) {

        int newSize = 1;

        while (newSize < size) {
            newSize = newSize << 1;
        }

        size = newSize;
        objects = new Holder[size];

        for (int x = 0; x < size; x++) {
            objects[x] = new Holder<>(factory.create());
        }

        mask = size - 1;
        releasePointer = size;
        BASE = Unsafe.instance.arrayBaseOffset(Holder[].class);
        INDEXSCALE = Unsafe.instance.arrayIndexScale(Holder[].class);
        ASHIFT = 31 - Integer.numberOfLeadingZeros((int) INDEXSCALE);
    }

    public Holder<T> take() {
        int localTakePointer;

        Holder<T> localObject = localValue.get();
        if (localObject != null) {
            if (localObject.state.compareAndSet(Holder.FREE, Holder.USED)) {
                return localObject;
            }
        }

        while (releasePointer != (localTakePointer = takePointer)) {
            int index = localTakePointer & mask;
            Holder<T> holder = objects[index];
            if (holder != null && Unsafe.instance.compareAndSwapObject(objects, (index << ASHIFT) + BASE, holder, null)) {
                takePointer = localTakePointer + 1;
                if (holder.state.compareAndSet(Holder.FREE, Holder.USED)) {
                    localValue.set(holder);
                    return holder;
                }
            }
        }
        return null;
    }

    public void release(Holder<T> object) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            int localValue = releasePointer;
            long index = ((localValue & mask) << ASHIFT) + BASE;
            if (object.state.compareAndSet(Holder.USED, Holder.FREE)) {
                Unsafe.instance.putOrderedObject(objects, index, object);
                releasePointer = localValue + 1;
            } else {
                throw new IllegalArgumentException("Invalid reference passed");
            }
        } finally {
            lock.unlock();
        }
    }

    public static class Holder<T> {
        private T value;
        public static final int FREE = 0;
        public static final int USED = 1;

        private AtomicInteger state = new AtomicInteger(FREE);

        public Holder(T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }
    }

    public static interface PoolFactory<T> {
        public T create();
    }
}