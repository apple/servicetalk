/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.concurrent.context;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.arraycopy;
import static java.lang.reflect.Array.newInstance;
import static java.util.Arrays.copyOf;
import static java.util.Objects.requireNonNull;

/**
 * This class is useful when two iterations must be done over the same set of elements.
 * Using a {@link CopyOnWriteArraySet} would make it difficult (or impossible) to ensure two successive iterations
 * see the same set of elements because only a forward {@link Iterator} is exposed.
 * <p>
 * Using a {@link CopyOnWriteArrayList} exposes a {@link ListIterator} but that would require 3 traversals
 * of the list for each event.
 * <p>
 * This implementation is currently optimized for low volume modifications and high volume usage of {@link #array()} for
 * iteration.
 */
final class SimpleCopyOnWriteSet<T> {
    private final T[] emptySet;
    private final Class<T> clazz;
    private final AtomicReference<T[]> arrayRef;

    @SuppressWarnings("unchecked")
    SimpleCopyOnWriteSet(Class<T> c) {
        clazz = requireNonNull(c);
        emptySet = (T[]) newInstance(clazz, 0);
        arrayRef = new AtomicReference<>(emptySet);
    }

    boolean add(T element) {
        requireNonNull(element);
        for (;;) {
            T[] array = arrayRef.get();
            int i = indexOf(element, array);
            if (i >= 0) {
                return false;
            }
            T[] newArray = copyOf(array, array.length + 1);
            newArray[array.length] = element;
            if (arrayRef.compareAndSet(array, newArray)) {
                return true;
            }
        }
    }

    boolean remove(T element) {
        for (;;) {
            T[] array = arrayRef.get();
            int i = indexOf(element, array);
            if (i < 0) {
                return false;
            }
            if (array.length == 1 && arrayRef.compareAndSet(array, emptySet)) {
                return true;
            }
            @SuppressWarnings("unchecked")
            T[] newArray = (T[]) newInstance(clazz, array.length - 1);
            arraycopy(array, 0, newArray, 0, i);
            arraycopy(array, i + 1, newArray, i, array.length - i - 1);
            if (arrayRef.compareAndSet(array, newArray)) {
                return true;
            }
        }
    }

    void clear() {
        arrayRef.set(emptySet);
    }

    /**
     * Get a snapshot of the underlying array. Use with caution as modification of the array will invalidate the constructs of this class!
     * @return a snapshot of the underlying array. Use with caution as modification of the array will invalidate the constructs of this class!
     */
    T[] array() {
        return arrayRef.get();
    }

    private static <X> int indexOf(X l, X[] array) {
        for (int i = 0; i < array.length; ++i) {
            // Null elements are not permitted in the array, so no null check is necessary.
            if (array[i].equals(l)) {
                return i;
            }
        }
        return -1;
    }
}
