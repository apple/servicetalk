/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.internal.ContextMapUtils;
import io.servicetalk.context.api.ContextMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ContextMapUtils.ensureType;
import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

/**
 * This class provides a Copy-on-Write map behavior. It features special cases for cardinality of less than 7 elements.
 * Less than 7 elements was chosen because it is not common to have more than this number of
 * {@link ContextMap.Key}-value entries in a single {@link ContextMap}. Common {@link ContextMap.Key}-value entries are
 * (tracing, MDC, auth, 3-custom user entries).
 */
final class CopyOnWriteContextMap implements ContextMap {
    private static final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater =
            AtomicReferenceFieldUpdater.newUpdater(CopyOnWriteContextMap.class, CopyContextMap.class, "map");
    private volatile CopyContextMap map;

    CopyOnWriteContextMap() {
        this(EmptyContextMap.INSTANCE);
    }

    private CopyOnWriteContextMap(CopyContextMap map) {
        this.map = map;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(final Key<?> key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(@Nullable final Object value) {
        return map.containsValue(value);
    }

    @Override
    public <T> boolean contains(final Key<T> key, @Nullable final T value) {
        return map.contains(key, value);
    }

    @Nullable
    @Override
    public <T> T get(final Key<T> key) {
        return map.get(key);
    }

    @Nullable
    @Override
    public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    @Nullable
    @Override
    public <T> T put(final Key<T> key, @Nullable final T value) {
        return map.put(key, value, this, mapUpdater);
    }

    @Nullable
    @Override
    public <T> T putIfAbsent(final Key<T> key, @Nullable final T value) {
        return map.putIfAbsent(key, value, this, mapUpdater);
    }

    @Nullable
    @Override
    public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction) {
        return map.computeIfAbsent(key, computeFunction, this, mapUpdater);
    }

    @Override
    public void putAll(final ContextMap map) {
        final int size = map.size();
        if (size < 1) {
            return;
        }
        for (;;) {
            CopyContextMap contextMap = this.map;
            if (mapUpdater.compareAndSet(this, contextMap, contextMap.putAll(size, map::forEach))) {
                break;
            }
        }
    }

    @Override
    public void putAll(final Map<Key<?>, Object> map) {
        final int size = map.size();
        if (size < 1) {
            return;
        }
        for (;;) {
            CopyContextMap contextMap = this.map;
            if (mapUpdater.compareAndSet(this, contextMap, contextMap.putAll(size, map::forEach))) {
                break;
            }
        }
    }

    @Nullable
    @Override
    public <T> T remove(final Key<T> key) {
        return map.remove(key, this, mapUpdater);
    }

    @Override
    public boolean removeAll(final Iterable<Key<?>> keys) {
        return map.removeAll(keys, this, mapUpdater);
    }

    @Override
    public void clear() {
        map = EmptyContextMap.INSTANCE;
    }

    @Nullable
    @Override
    public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
        return map.forEach(consumer);
    }

    @Override
    public ContextMap copy() {
        return new CopyOnWriteContextMap(map);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContextMap)) {
            return false;
        }
        if (o instanceof CopyOnWriteContextMap) {
            return map.equals(((CopyOnWriteContextMap) o).map);
        }
        return ContextMapUtils.equals(this, (ContextMap) o);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return ContextMapUtils.toString(this);
    }

    private interface CopyContextMap {

        int size();

        boolean isEmpty();

        boolean containsKey(Key<?> key);

        boolean containsValue(@Nullable Object value);

        <T> boolean contains(Key<T> key, @Nullable T value);

        @Nullable
        <T> T get(Key<T> key);

        @Nullable
        <T> T getOrDefault(Key<T> key, T defaultValue);

        @Nullable
        <T> T put(Key<T> key, @Nullable T value, CopyOnWriteContextMap owner,
                  AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater);

        @Nullable
        <T> T putIfAbsent(Key<T> key, @Nullable T value, CopyOnWriteContextMap owner,
                          AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater);

        @Nullable
        <T> T computeIfAbsent(Key<T> key, Function<Key<T>, T> computeFunction, CopyOnWriteContextMap owner,
                              AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater);

        CopyContextMap putAll(int mapSize, Consumer<PutAllBuilder> forEach);

        @Nullable
        <T> T remove(Key<T> key, CopyOnWriteContextMap owner,
                     AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater);

        boolean removeAll(Iterable<Key<?>> keys, CopyOnWriteContextMap owner,
                          AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater);

        @Nullable
        Key<?> forEach(BiPredicate<Key<?>, Object> consumer);

        @Override
        boolean equals(Object o);

        @Override
        int hashCode();
    }

    private static final class EmptyContextMap implements CopyContextMap {
        static final CopyContextMap INSTANCE = new EmptyContextMap();

        private EmptyContextMap() {
            // singleton
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean containsKey(final Key<?> key) {
            return false;
        }

        @Override
        public boolean containsValue(@Nullable final Object value) {
            return false;
        }

        @Override
        public <T> boolean contains(final Key<T> key, @Nullable final T value) {
            return false;
        }

        @Nullable
        @Override
        public <T> T get(final Key<T> key) {
            return null;
        }

        @Override
        public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
            return defaultValue;
        }

        @Nullable
        @Override
        public <T> T put(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            return mapUpdater.compareAndSet(owner, this, new OneContextMap(requireNonNull(key), value)) ?
                    null : owner.put(key, value);
        }

        @Nullable
        @Override
        public <T> T putIfAbsent(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            return mapUpdater.compareAndSet(owner, this, new OneContextMap(requireNonNull(key), value)) ?
                    null : owner.putIfAbsent(key, value);
        }

        @Nullable
        @Override
        public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction,
                     final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            final T value = computeFunction.apply(requireNonNull(key));
            return mapUpdater.compareAndSet(owner, this, new OneContextMap(key, value)) ?
                    value : owner.computeIfAbsent(key, computeFunction);
        }

        @Override
        public CopyContextMap putAll(final int mapSize, final Consumer<PutAllBuilder> forEach) {
            final PutAllBuilder builder = new PutAllBuilder(mapSize);
            forEach.accept(builder);
            return builder.build();
        }

        @Nullable
        @Override
        public <T> T remove(final Key<T> key, final CopyOnWriteContextMap owner,
                        final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            requireNonNull(key);
            return null;
        }

        @Override
        public boolean removeAll(final Iterable<Key<?>> keys, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            requireNonNull(keys);
            return false;
        }

        @Nullable
        @Override
        public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
            return null;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(final Object o) {
            return this == o;   // this is a singleton
        }
    }

    private static final class OneContextMap implements CopyContextMap {

        private final Key<?> keyOne;
        @Nullable
        private final Object valueOne;

        OneContextMap(final Key<?> keyOne, @Nullable final Object valueOne) {
            this.keyOne = keyOne;
            this.valueOne = valueOne;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean containsKey(final Key<?> key) {
            return key.equals(keyOne);
        }

        @Override
        public boolean containsValue(@Nullable final Object value) {
            return Objects.equals(value, valueOne);
        }

        @Override
        public <T> boolean contains(final Key<T> key, @Nullable final T value) {
            return containsKey(key) && containsValue(value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(final Key<T> key) {
            return key.equals(keyOne) ? (T) valueOne : null;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
            return key.equals(keyOne) ? (T) valueOne : defaultValue;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T put(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                return mapUpdater.compareAndSet(owner, this, new OneContextMap(key, value)) ?
                        (T) valueOne : owner.put(key, value);
            }
            return mapUpdater.compareAndSet(owner, this, new TwoContextMap(keyOne, valueOne, key, value)) ?
                    null : owner.put(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T putIfAbsent(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                if (valueOne == null) {
                    return mapUpdater.compareAndSet(owner, this, new OneContextMap(key, value)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueOne;
            }
            return mapUpdater.compareAndSet(owner, this, new TwoContextMap(keyOne, valueOne, key, value)) ?
                    null : owner.putIfAbsent(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction,
                     final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                if (valueOne == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new OneContextMap(key, value)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueOne;
            }
            final T value = computeFunction.apply(key);
            return mapUpdater.compareAndSet(owner, this, new TwoContextMap(keyOne, valueOne, key, value)) ?
                    value : owner.computeIfAbsent(key, computeFunction);
        }

        @Override
        public CopyContextMap putAll(final int mapSize, final Consumer<PutAllBuilder> forEach) {
            final PutAllBuilder builder = new PutAllBuilder(size() + mapSize);
            builder.addPair(keyOne, valueOne);
            forEach.accept(builder);
            return builder.build();
        }

        @SuppressWarnings("unchecked")
        @Nullable
        @Override
        public <T> T remove(final Key<T> key, CopyOnWriteContextMap owner,
                        final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                return mapUpdater.compareAndSet(owner, this, EmptyContextMap.INSTANCE) ?
                        (T) valueOne : owner.remove(key);
            }
            return null;
        }

        @Override
        public boolean removeAll(final Iterable<Key<?>> keys, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            for (Key<?> k : keys) {
                if (k.equals(keyOne)) {
                    return mapUpdater.compareAndSet(owner, this, EmptyContextMap.INSTANCE) ||
                            owner.removeAll(keys);
                }
            }
            return false;
        }

        @Nullable
        @Override
        public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
            return consumer.test(keyOne, valueOne) ? null : keyOne;
        }

        @Override
        public int hashCode() {
            int result = keyOne.hashCode();
            result = 31 * result + Objects.hashCode(valueOne);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final OneContextMap that = (OneContextMap) o;
            return keyOne.equals(that.keyOne) && Objects.equals(valueOne, that.valueOne);
        }
    }

    private static final class TwoContextMap implements CopyContextMap {

        private final Key<?> keyOne;
        @Nullable
        private final Object valueOne;

        private final Key<?> keyTwo;
        @Nullable
        private final Object valueTwo;

        TwoContextMap(final Key<?> keyOne, @Nullable final Object valueOne,
                      final Key<?> keyTwo, @Nullable final Object valueTwo) {
            this.keyOne = keyOne;
            this.valueOne = valueOne;
            this.keyTwo = keyTwo;
            this.valueTwo = valueTwo;
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean containsKey(final Key<?> key) {
            return key.equals(keyOne) || key.equals(keyTwo);
        }

        @Override
        public boolean containsValue(@Nullable final Object value) {
            return Objects.equals(value, valueOne) || Objects.equals(value, valueTwo);
        }

        @Override
        public <T> boolean contains(final Key<T> key, @Nullable final T value) {
            return (key.equals(keyOne) && Objects.equals(value, valueOne)) ||
                    (key.equals(keyTwo) && Objects.equals(value, valueTwo));
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(final Key<T> key) {
            return key.equals(keyOne) ? (T) valueOne : key.equals(keyTwo) ? (T) valueTwo : null;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
            return key.equals(keyOne) ? (T) valueOne : key.equals(keyTwo) ? (T) valueTwo : defaultValue;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T put(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                return mapUpdater.compareAndSet(owner, this, new TwoContextMap(key, value, keyTwo, valueTwo)) ?
                        (T) valueOne : owner.put(key, value);
            }
            if (key.equals(keyTwo)) {
                return mapUpdater.compareAndSet(owner, this, new TwoContextMap(keyOne, valueOne, key, value)) ?
                        (T) valueTwo : owner.put(key, value);
            }
            return mapUpdater.compareAndSet(owner, this,
                    new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, key, value)) ?
                    null : owner.put(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T putIfAbsent(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                if (valueOne == null) {
                    return mapUpdater.compareAndSet(owner, this, new TwoContextMap(key, value, keyTwo, valueTwo)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueOne;
            }
            if (key.equals(keyTwo)) {
                if (valueTwo == null) {
                    return mapUpdater.compareAndSet(owner, this, new TwoContextMap(keyOne, valueOne, key, value)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueTwo;
            }
            return mapUpdater.compareAndSet(owner, this,
                    new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, key, value)) ?
                    null : owner.putIfAbsent(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction,
                     final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                if (valueOne == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new TwoContextMap(key, value, keyTwo, valueTwo)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueOne;
            }
            if (key.equals(keyTwo)) {
                if (valueTwo == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new TwoContextMap(keyOne, valueOne, key, value)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueTwo;
            }
            final T value = computeFunction.apply(key);
            return mapUpdater.compareAndSet(owner, this,
                    new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, key, value)) ?
                    value : owner.computeIfAbsent(key, computeFunction);
        }

        @Override
        public CopyContextMap putAll(final int mapSize, final Consumer<PutAllBuilder> forEach) {
            final PutAllBuilder builder = new PutAllBuilder(size() + mapSize);
            builder.addPair(keyOne, valueOne);
            builder.addPair(keyTwo, valueTwo);
            forEach.accept(builder);
            return builder.build();
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T remove(final Key<T> key, final CopyOnWriteContextMap owner,
                        final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                return mapUpdater.compareAndSet(owner, this, new OneContextMap(keyTwo, valueTwo)) ?
                        (T) valueOne : owner.remove(key);
            }
            if (key.equals(keyTwo)) {
                return mapUpdater.compareAndSet(owner, this, new OneContextMap(keyOne, valueOne)) ?
                        (T) valueTwo : owner.remove(key);
            }
            return null;
        }

        @Override
        public boolean removeAll(final Iterable<Key<?>> keys, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            final CopyContextMap newMap = removeAll(removeIndexMask(keys));
            if (newMap == null) {
                return false;
            }
            return mapUpdater.compareAndSet(owner, this, newMap) || owner.removeAll(keys);
        }

        private int removeIndexMask(final Iterable<Key<?>> keys) {
            int mask = 0;
            for (Key<?> k : keys) {
                if (k.equals(keyOne)) {
                    mask |= 0x1;
                } else if (k.equals(keyTwo)) {
                    mask |= 0x2;
                }
            }
            return mask;
        }

        @Nullable
        private CopyContextMap removeAll(final int removeIndexMask) {
            if ((removeIndexMask & 0x3) == 0x3) {
                return EmptyContextMap.INSTANCE;
            }
            if ((removeIndexMask & 0x2) == 0x2) {
                return new OneContextMap(keyOne, valueOne);
            }
            if ((removeIndexMask & 0x1) == 0x1) {
                return new OneContextMap(keyTwo, valueTwo);
            }
            return null;
        }

        @Nullable
        @Override
        public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
            if (!consumer.test(keyOne, valueOne)) {
                return keyOne;
            }
            return consumer.test(keyTwo, valueTwo) ? null : keyTwo;
        }

        @Override
        public int hashCode() {
            int result = keyOne.hashCode();
            result = 31 * result + Objects.hashCode(valueOne);
            result = 31 * result + keyTwo.hashCode();
            result = 31 * result + Objects.hashCode(valueTwo);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final TwoContextMap that = (TwoContextMap) o;
            return keyOne.equals(that.keyOne) && Objects.equals(valueOne, that.valueOne) &&
                    keyTwo.equals(that.keyTwo) && Objects.equals(valueTwo, that.valueTwo);
        }
    }

    private static final class ThreeContextMap implements CopyContextMap {

        private final Key<?> keyOne;
        @Nullable
        private final Object valueOne;

        private final Key<?> keyTwo;
        @Nullable
        private final Object valueTwo;

        private final Key<?> keyThree;
        @Nullable
        private final Object valueThree;

        ThreeContextMap(final Key<?> keyOne, @Nullable final Object valueOne,
                        final Key<?> keyTwo, @Nullable final Object valueTwo,
                        final Key<?> keyThree, @Nullable final Object valueThree) {
            this.keyOne = keyOne;
            this.valueOne = valueOne;
            this.keyTwo = keyTwo;
            this.valueTwo = valueTwo;
            this.keyThree = keyThree;
            this.valueThree = valueThree;
        }

        @Override
        public int size() {
            return 3;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean containsKey(final Key<?> key) {
            return key.equals(keyOne) || key.equals(keyTwo) || key.equals(keyThree);
        }

        @Override
        public boolean containsValue(@Nullable final Object value) {
            return Objects.equals(value, valueOne) || Objects.equals(value, valueTwo) ||
                    Objects.equals(value, valueThree);
        }

        @Override
        public <T> boolean contains(final Key<T> key, @Nullable final T value) {
            return (key.equals(keyOne) && Objects.equals(value, valueOne)) ||
                    (key.equals(keyTwo) && Objects.equals(value, valueTwo)) ||
                    (key.equals(keyThree) && Objects.equals(value, valueThree));
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(final Key<T> key) {
            return key.equals(keyOne) ? (T) valueOne : key.equals(keyTwo) ? (T) valueTwo : key.equals(keyThree) ?
                    (T) valueThree : null;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
            return key.equals(keyOne) ? (T) valueOne : key.equals(keyTwo) ? (T) valueTwo : key.equals(keyThree) ?
                    (T) valueThree : defaultValue;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T put(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (keyOne.equals(key)) {
                return mapUpdater.compareAndSet(owner, this,
                        new ThreeContextMap(key, value, keyTwo, valueTwo, keyThree, valueThree)) ?
                        (T) valueOne : owner.put(key, value);
            }
            if (keyTwo.equals(key)) {
                return mapUpdater.compareAndSet(owner, this,
                        new ThreeContextMap(keyOne, valueOne, key, value, keyThree, valueThree)) ?
                        (T) valueTwo : owner.put(key, value);
            }
            if (keyThree.equals(key)) {
                return mapUpdater.compareAndSet(owner, this,
                        new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, key, value)) ?
                        (T) valueThree : owner.put(key, value);
            }
            return mapUpdater.compareAndSet(owner, this,
                    new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree, key, value)) ?
                    null : owner.put(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T putIfAbsent(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (keyOne.equals(key)) {
                if (valueOne == null) {
                    return mapUpdater.compareAndSet(owner, this,
                            new ThreeContextMap(key, value, keyTwo, valueTwo, keyThree, valueThree)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueOne;
            }
            if (keyTwo.equals(key)) {
                if (valueTwo == null) {
                    return mapUpdater.compareAndSet(owner, this,
                            new ThreeContextMap(keyOne, valueOne, key, value, keyThree, valueThree)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueTwo;
            }
            if (keyThree.equals(key)) {
                if (valueThree == null) {
                    return mapUpdater.compareAndSet(owner, this,
                            new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, key, value)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueThree;
            }
            return mapUpdater.compareAndSet(owner, this,
                    new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree, key, value)) ?
                    null : owner.putIfAbsent(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction,
                     final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (keyOne.equals(key)) {
                if (valueOne == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this,
                            new ThreeContextMap(key, value, keyTwo, valueTwo, keyThree, valueThree)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueOne;
            }
            if (keyTwo.equals(key)) {
                if (valueTwo == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this,
                            new ThreeContextMap(keyOne, valueOne, key, value, keyThree, valueThree)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueTwo;
            }
            if (keyThree.equals(key)) {
                if (valueThree == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this,
                            new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, key, value)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueThree;
            }
            final T value = computeFunction.apply(key);
            return mapUpdater.compareAndSet(owner, this,
                    new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree, key, value)) ?
                    value : owner.computeIfAbsent(key, computeFunction);
        }

        @Override
        public CopyContextMap putAll(final int mapSize, final Consumer<PutAllBuilder> forEach) {
            final PutAllBuilder builder = new PutAllBuilder(size() + mapSize);
            builder.addPair(keyOne, valueOne);
            builder.addPair(keyTwo, valueTwo);
            builder.addPair(keyThree, valueThree);
            forEach.accept(builder);
            return builder.build();
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T remove(final Key<T> key, final CopyOnWriteContextMap owner,
                        final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                return mapUpdater.compareAndSet(owner, this,
                        new TwoContextMap(keyTwo, valueTwo, keyThree, valueThree)) ?
                        (T) valueOne : owner.remove(key);
            }
            if (key.equals(keyTwo)) {
                return mapUpdater.compareAndSet(owner, this,
                        new TwoContextMap(keyOne, valueOne, keyThree, valueThree)) ?
                        (T) valueTwo : owner.remove(key);
            }
            if (key.equals(keyThree)) {
                return mapUpdater.compareAndSet(owner, this,
                        new TwoContextMap(keyOne, valueOne, keyTwo, valueTwo)) ?
                        (T) valueThree : owner.remove(key);
            }
            return null;
        }

        @Override
        public boolean removeAll(final Iterable<Key<?>> keys, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            final CopyContextMap newMap = removeAll(removeIndexMask(keys));
            if (newMap == null) {
                return false;
            }
            return mapUpdater.compareAndSet(owner, this, newMap) || owner.removeAll(keys);
        }

        private int removeIndexMask(final Iterable<Key<?>> keys) {
            int mask = 0;
            for (ContextMap.Key<?> k : keys) {
                if (k.equals(keyOne)) {
                    mask |= 0x1;
                } else if (k.equals(keyTwo)) {
                    mask |= 0x2;
                } else if (k.equals(keyThree)) {
                    mask |= 0x4;
                }
            }
            return mask;
        }

        @Nullable
        private CopyContextMap removeAll(final int removeIndexMask) {
            if ((removeIndexMask & 0x7) == 0x7) {
                return EmptyContextMap.INSTANCE;
            }
            if ((removeIndexMask & 0x4) == 0x4) {
                if ((removeIndexMask & 0x2) == 0x2) {
                    return new OneContextMap(keyOne, valueOne);
                }
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new OneContextMap(keyTwo, valueTwo);
                }
                return new TwoContextMap(keyOne, valueOne, keyTwo, valueTwo);
            }
            if ((removeIndexMask & 0x2) == 0x2) {
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new OneContextMap(keyThree, valueThree);
                }
                return new TwoContextMap(keyOne, valueOne, keyThree, valueThree);
            }
            if ((removeIndexMask & 0x1) == 0x1) {
                return new TwoContextMap(keyTwo, valueTwo, keyThree, valueThree);
            }
            return null;
        }

        @Nullable
        @Override
        public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
            if (!consumer.test(keyOne, valueOne)) {
                return keyOne;
            }
            if (!consumer.test(keyTwo, valueTwo)) {
                return keyTwo;
            }
            return consumer.test(keyThree, valueThree) ? null : keyThree;
        }

        @Override
        public int hashCode() {
            int result = keyOne.hashCode();
            result = 31 * result + Objects.hashCode(valueOne);
            result = 31 * result + keyTwo.hashCode();
            result = 31 * result + Objects.hashCode(valueTwo);
            result = 31 * result + keyThree.hashCode();
            result = 31 * result + Objects.hashCode(valueThree);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ThreeContextMap that = (ThreeContextMap) o;
            return keyOne.equals(that.keyOne) && Objects.equals(valueOne, that.valueOne) &&
                    keyTwo.equals(that.keyTwo) && Objects.equals(valueTwo, that.valueTwo) &&
                    keyThree.equals(that.keyThree) && Objects.equals(valueThree, that.valueThree);
        }
    }

    private static final class FourContextMap implements CopyContextMap {

        private final Key<?> keyOne;
        @Nullable
        private final Object valueOne;

        private final Key<?> keyTwo;
        @Nullable
        private final Object valueTwo;

        private final Key<?> keyThree;
        @Nullable
        private final Object valueThree;

        private final Key<?> keyFour;
        @Nullable
        private final Object valueFour;

        FourContextMap(final Key<?> keyOne, @Nullable final Object valueOne,
                       final Key<?> keyTwo, @Nullable final Object valueTwo,
                       final Key<?> keyThree, @Nullable final Object valueThree,
                       final Key<?> keyFour, @Nullable final Object valueFour) {
            this.keyOne = keyOne;
            this.valueOne = valueOne;
            this.keyTwo = keyTwo;
            this.valueTwo = valueTwo;
            this.keyThree = keyThree;
            this.valueThree = valueThree;
            this.keyFour = keyFour;
            this.valueFour = valueFour;
        }

        @Override
        public int size() {
            return 4;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean containsKey(final Key<?> key) {
            return key.equals(keyOne) || key.equals(keyTwo) || key.equals(keyThree) || key.equals(keyFour);
        }

        @Override
        public boolean containsValue(@Nullable final Object value) {
            return Objects.equals(value, valueOne) || Objects.equals(value, valueTwo) ||
                    Objects.equals(value, valueThree) || Objects.equals(value, valueFour);
        }

        @Override
        public <T> boolean contains(final Key<T> key, @Nullable final T value) {
            return (key.equals(keyOne) && Objects.equals(value, valueOne)) ||
                    (key.equals(keyTwo) && Objects.equals(value, valueTwo)) ||
                    (key.equals(keyThree) && Objects.equals(value, valueThree)) ||
                    (key.equals(keyFour) && Objects.equals(value, valueFour));
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(final Key<T> key) {
            return key.equals(keyOne) ? (T) valueOne : key.equals(keyTwo) ? (T) valueTwo : key.equals(keyThree) ?
                    (T) valueThree : key.equals(keyFour) ? (T) valueFour : null;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
            return key.equals(keyOne) ? (T) valueOne : key.equals(keyTwo) ? (T) valueTwo : key.equals(keyThree) ?
                    (T) valueThree : key.equals(keyFour) ? (T) valueFour : defaultValue;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T put(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                return mapUpdater.compareAndSet(owner, this, new FourContextMap(key, value,
                        keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour)) ?
                        (T) valueOne : owner.put(key, value);
            }
            if (key.equals(keyTwo)) {
                return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                        key, value, keyThree, valueThree, keyFour, valueFour)) ?
                        (T) valueTwo : owner.put(key, value);
            }
            if (key.equals(keyThree)) {
                return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                        keyTwo, valueTwo, key, value, keyFour, valueFour)) ?
                        (T) valueThree : owner.put(key, value);
            }
            if (key.equals(keyFour)) {
                return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                        keyTwo, valueTwo, keyThree, valueThree, key, value)) ?
                        (T) valueFour : owner.put(key, value);
            }
            return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                    keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, key, value)) ?
                    null : owner.put(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T putIfAbsent(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                if (valueOne == null) {
                    return mapUpdater.compareAndSet(owner, this, new FourContextMap(key, value,
                            keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueOne;
            }
            if (key.equals(keyTwo)) {
                if (valueTwo == null) {
                    return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                            key, value, keyThree, valueThree, keyFour, valueFour)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueTwo;
            }
            if (key.equals(keyThree)) {
                if (valueThree == null) {
                    return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, key, value, keyFour, valueFour)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueThree;
            }
            if (key.equals(keyFour)) {
                if (valueFour == null) {
                    return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, key, value)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueFour;
            }
            return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                    keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, key, value)) ?
                    null : owner.putIfAbsent(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction,
                     final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                if (valueOne == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new FourContextMap(key, value,
                            keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueOne;
            }
            if (key.equals(keyTwo)) {
                if (valueTwo == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                            key, value, keyThree, valueThree, keyFour, valueFour)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueTwo;
            }
            if (key.equals(keyThree)) {
                if (valueThree == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, key, value, keyFour, valueFour)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueThree;
            }
            if (key.equals(keyFour)) {
                if (valueFour == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, key, value)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueFour;
            }
            final T value = computeFunction.apply(key);
            return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                    keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, key, value)) ?
                    value : owner.computeIfAbsent(key, computeFunction);
        }

        @Override
        public CopyContextMap putAll(final int mapSize, final Consumer<PutAllBuilder> forEach) {
            final PutAllBuilder builder = new PutAllBuilder(size() + mapSize);
            builder.addPair(keyOne, valueOne);
            builder.addPair(keyTwo, valueTwo);
            builder.addPair(keyThree, valueThree);
            builder.addPair(keyFour, valueFour);
            forEach.accept(builder);
            return builder.build();
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T remove(final Key<T> key, final CopyOnWriteContextMap owner,
                        final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                return mapUpdater.compareAndSet(owner, this,
                        new ThreeContextMap(keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour)) ?
                        (T) valueOne : owner.remove(key);
            }
            if (key.equals(keyTwo)) {
                return mapUpdater.compareAndSet(owner, this,
                        new ThreeContextMap(keyOne, valueOne, keyThree, valueThree, keyFour, valueFour)) ?
                        (T) valueTwo : owner.remove(key);
            }
            if (key.equals(keyThree)) {
                return mapUpdater.compareAndSet(owner, this,
                        new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, keyFour, valueFour)) ?
                        (T) valueThree : owner.remove(key);
            }
            if (key.equals(keyFour)) {
                return mapUpdater.compareAndSet(owner, this,
                        new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree)) ?
                        (T) valueFour : owner.remove(key);
            }
            return null;
        }

        @Override
        public boolean removeAll(final Iterable<Key<?>> keys, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            CopyContextMap newMap = removeAll(removeIndexMask(keys));
            if (newMap == null) {
                return false;
            }
            return mapUpdater.compareAndSet(owner, this, newMap) || owner.removeAll(keys);
        }

        private int removeIndexMask(final Iterable<ContextMap.Key<?>> keys) {
            int mask = 0;
            for (ContextMap.Key<?> k : keys) {
                if (k.equals(keyOne)) {
                    mask |= 0x1;
                } else if (k.equals(keyTwo)) {
                    mask |= 0x2;
                } else if (k.equals(keyThree)) {
                    mask |= 0x4;
                } else if (k.equals(keyFour)) {
                    mask |= 0x8;
                }
            }
            return mask;
        }

        @Nullable
        private CopyContextMap removeAll(final int removeIndexMask) {
            if ((removeIndexMask & 0xF) == 0xF) {
                return EmptyContextMap.INSTANCE;
            }
            if ((removeIndexMask & 0x8) == 0x8) {
                if ((removeIndexMask & 0x4) == 0x4) {
                    if ((removeIndexMask & 0x2) == 0x2) {
                        return new OneContextMap(keyOne, valueOne);
                    }
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new OneContextMap(keyTwo, valueTwo);
                    }
                    return new TwoContextMap(keyOne, valueOne, keyTwo, valueTwo);
                }
                if ((removeIndexMask & 0x2) == 0x2) {
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new OneContextMap(keyThree, valueThree);
                    }
                    return new TwoContextMap(keyOne, valueOne, keyThree, valueThree);
                }
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new TwoContextMap(keyTwo, valueTwo, keyThree, valueThree);
                }
                return new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree);
            }
            if ((removeIndexMask & 0x4) == 0x4) {
                if ((removeIndexMask & 0x2) == 0x2) {
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new OneContextMap(keyFour, valueFour);
                    }
                    return new TwoContextMap(keyOne, valueOne, keyFour, valueFour);
                }
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new TwoContextMap(keyTwo, valueTwo, keyFour, valueFour);
                }
                return new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, keyFour, valueFour);
            }
            if ((removeIndexMask & 0x2) == 0x2) {
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new TwoContextMap(keyThree, valueThree, keyFour, valueFour);
                }
                return new ThreeContextMap(keyOne, valueOne, keyThree, valueThree, keyFour, valueFour);
            }
            if ((removeIndexMask & 0x1) == 0x1) {
                return new ThreeContextMap(keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour);
            }
            return null;
        }

        @Nullable
        @Override
        public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
            if (!consumer.test(keyOne, valueOne)) {
                return keyOne;
            }
            if (!consumer.test(keyTwo, valueTwo)) {
                return keyTwo;
            }
            if (!consumer.test(keyThree, valueThree)) {
                return keyThree;
            }
            return consumer.test(keyFour, valueFour) ? null : keyFour;
        }

        @Override
        public int hashCode() {
            int result = keyOne.hashCode();
            result = 31 * result + Objects.hashCode(valueOne);
            result = 31 * result + keyTwo.hashCode();
            result = 31 * result + Objects.hashCode(valueTwo);
            result = 31 * result + keyThree.hashCode();
            result = 31 * result + Objects.hashCode(valueThree);
            result = 31 * result + keyFour.hashCode();
            result = 31 * result + Objects.hashCode(valueFour);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final FourContextMap that = (FourContextMap) o;
            return keyOne.equals(that.keyOne) && Objects.equals(valueOne, that.valueOne) &&
                    keyTwo.equals(that.keyTwo) && Objects.equals(valueTwo, that.valueTwo) &&
                    keyThree.equals(that.keyThree) && Objects.equals(valueThree, that.valueThree) &&
                    keyFour.equals(that.keyFour) && Objects.equals(valueFour, that.valueFour);
        }
    }

    private static final class FiveContextMap implements CopyContextMap {

        private final Key<?> keyOne;
        @Nullable
        private final Object valueOne;

        private final Key<?> keyTwo;
        @Nullable
        private final Object valueTwo;

        private final Key<?> keyThree;
        @Nullable
        private final Object valueThree;

        private final Key<?> keyFour;
        @Nullable
        private final Object valueFour;

        private final Key<?> keyFive;
        @Nullable
        private final Object valueFive;

        FiveContextMap(final Key<?> keyOne, @Nullable final Object valueOne,
                       final Key<?> keyTwo, @Nullable final Object valueTwo,
                       final Key<?> keyThree, @Nullable final Object valueThree,
                       final Key<?> keyFour, @Nullable final Object valueFour,
                       final Key<?> keyFive, @Nullable final Object valueFive) {
            this.keyOne = keyOne;
            this.valueOne = valueOne;
            this.keyTwo = keyTwo;
            this.valueTwo = valueTwo;
            this.keyThree = keyThree;
            this.valueThree = valueThree;
            this.keyFour = keyFour;
            this.valueFour = valueFour;
            this.keyFive = keyFive;
            this.valueFive = valueFive;
        }

        @Override
        public int size() {
            return 5;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean containsKey(final Key<?> key) {
            return key.equals(keyOne) || key.equals(keyTwo) || key.equals(keyThree) || key.equals(keyFour) ||
                    key.equals(keyFive);
        }

        @Override
        public boolean containsValue(@Nullable final Object value) {
            return Objects.equals(value, valueOne) || Objects.equals(value, valueTwo) ||
                    Objects.equals(value, valueThree) || Objects.equals(value, valueFour) ||
                    Objects.equals(value, valueFive);
        }

        @Override
        public <T> boolean contains(final Key<T> key, @Nullable final T value) {
            return (key.equals(keyOne) && Objects.equals(value, valueOne)) ||
                    (key.equals(keyTwo) && Objects.equals(value, valueTwo)) ||
                    (key.equals(keyThree) && Objects.equals(value, valueThree)) ||
                    (key.equals(keyFour) && Objects.equals(value, valueFour)) ||
                    (key.equals(keyFive) && Objects.equals(value, valueFive));
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(final Key<T> key) {
            return key.equals(keyOne) ? (T) valueOne : key.equals(keyTwo) ? (T) valueTwo : key.equals(keyThree) ?
                    (T) valueThree : key.equals(keyFour) ? (T) valueFour : key.equals(keyFive) ? (T) valueFive : null;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
            return key.equals(keyOne) ? (T) valueOne : key.equals(keyTwo) ? (T) valueTwo : key.equals(keyThree) ?
                    (T) valueThree : key.equals(keyFour) ? (T) valueFour : key.equals(keyFive) ? (T) valueFive :
                    defaultValue;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T put(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                return mapUpdater.compareAndSet(owner, this, new FiveContextMap(key, value,
                        keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive)) ?
                        (T) valueOne : owner.put(key, value);
            }
            if (key.equals(keyTwo)) {
                return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                        key, value, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive)) ?
                        (T) valueTwo : owner.put(key, value);
            }
            if (key.equals(keyThree)) {
                return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                        keyTwo, valueTwo, key, value, keyFour, valueFour, keyFive, valueFive)) ?
                        (T) valueThree : owner.put(key, value);
            }
            if (key.equals(keyFour)) {
                return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                        keyTwo, valueTwo, keyThree, valueThree, key, value, keyFive, valueFive)) ?
                        (T) valueFour : owner.put(key, value);
            }
            if (key.equals(keyFive)) {
                return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                        keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, key, value)) ?
                        (T) valueFive : owner.put(key, value);
            }
            return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne,
                    keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, key, value)) ?
                    null : owner.put(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T putIfAbsent(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                if (valueOne == null) {
                    return mapUpdater.compareAndSet(owner, this, new FiveContextMap(key, value,
                            keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueOne;
            }
            if (key.equals(keyTwo)) {
                if (valueTwo == null) {
                    return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                            key, value, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueTwo;
            }
            if (key.equals(keyThree)) {
                if (valueThree == null) {
                    return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, key, value, keyFour, valueFour, keyFive, valueFive)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueThree;
            }
            if (key.equals(keyFour)) {
                if (valueFour == null) {
                    return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, key, value, keyFive, valueFive)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueFour;
            }
            if (key.equals(keyFive)) {
                if (valueFive == null) {
                    return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, key, value)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueFive;
            }
            return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne,
                    keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, key, value)) ?
                    null : owner.putIfAbsent(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction,
                     final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                if (valueOne == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new FiveContextMap(key, value,
                            keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueOne;
            }
            if (key.equals(keyTwo)) {
                if (valueTwo == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                            key, value, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueTwo;
            }
            if (key.equals(keyThree)) {
                if (valueThree == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, key, value, keyFour, valueFour, keyFive, valueFive)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueThree;
            }
            if (key.equals(keyFour)) {
                if (valueFour == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, key, value, keyFive, valueFive)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueFour;
            }
            if (key.equals(keyFive)) {
                if (valueFive == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, key, value)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueFive;
            }
            final T value = computeFunction.apply(key);
            return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne,
                    keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, key, value)) ?
                    value : owner.computeIfAbsent(key, computeFunction);
        }

        @Override
        public CopyContextMap putAll(final int mapSize, final Consumer<PutAllBuilder> forEach) {
            final PutAllBuilder builder = new PutAllBuilder(size() + mapSize);
            builder.addPair(keyOne, valueOne);
            builder.addPair(keyTwo, valueTwo);
            builder.addPair(keyThree, valueThree);
            builder.addPair(keyFour, valueFour);
            builder.addPair(keyFive, valueFive);
            forEach.accept(builder);
            return builder.build();
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T remove(final Key<T> key, final CopyOnWriteContextMap owner,
                    final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyTwo, valueTwo,
                        keyThree, valueThree, keyFour, valueFour, keyFive, valueFive)) ?
                        (T) valueOne : owner.remove(key);
            }
            if (key.equals(keyTwo)) {
                return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                        keyThree, valueThree, keyFour, valueFour, keyFive, valueFive)) ?
                        (T) valueTwo : owner.remove(key);
            }
            if (key.equals(keyThree)) {
                return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                        keyTwo, valueTwo, keyFour, valueFour, keyFive, valueFive)) ?
                        (T) valueThree : owner.remove(key);
            }
            if (key.equals(keyFour)) {
                return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                        keyTwo, valueTwo, keyThree, valueThree, keyFive, valueFive)) ?
                        (T) valueFour : owner.remove(key);
            }
            if (key.equals(keyFive)) {
                return mapUpdater.compareAndSet(owner, this, new FourContextMap(keyOne, valueOne,
                        keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour)) ?
                        (T) valueFive : owner.remove(key);
            }
            return null;
        }

        @Override
        public boolean removeAll(final Iterable<Key<?>> keys, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            final CopyContextMap newMap = removeAll(removeIndexMask(keys));
            if (newMap == null) {
                return false;
            }
            return mapUpdater.compareAndSet(owner, this, newMap) || owner.removeAll(keys);
        }

        private int removeIndexMask(final Iterable<ContextMap.Key<?>> keys) {
            int mask = 0;
            for (ContextMap.Key<?> k : keys) {
                if (k.equals(keyOne)) {
                    mask |= 0x1;
                } else if (k.equals(keyTwo)) {
                    mask |= 0x2;
                } else if (k.equals(keyThree)) {
                    mask |= 0x4;
                } else if (k.equals(keyFour)) {
                    mask |= 0x8;
                } else if (k.equals(keyFive)) {
                    mask |= 0x10;
                }
            }
            return mask;
        }

        @Nullable
        private CopyContextMap removeAll(final int removeIndexMask) {
            if ((removeIndexMask & 0x1F) == 0x1F) {
                return EmptyContextMap.INSTANCE;
            }
            if ((removeIndexMask & 0x10) == 0x10) {
                if ((removeIndexMask & 0x8) == 0x8) {
                    if ((removeIndexMask & 0x4) == 0x4) {
                        if ((removeIndexMask & 0x2) == 0x2) {
                            return new OneContextMap(keyOne, valueOne);
                        }
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new OneContextMap(keyTwo, valueTwo);
                        }
                        return new TwoContextMap(keyOne, valueOne, keyTwo, valueTwo);
                    }
                    if ((removeIndexMask & 0x2) == 0x2) {
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new OneContextMap(keyThree, valueThree);
                        }
                        return new TwoContextMap(keyOne, valueOne, keyThree, valueThree);
                    }
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new TwoContextMap(keyTwo, valueTwo, keyThree, valueThree);
                    }
                    return new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree);
                }
                if ((removeIndexMask & 0x4) == 0x4) {
                    if ((removeIndexMask & 0x2) == 0x2) {
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new OneContextMap(keyFour, valueFour);
                        }
                        return new TwoContextMap(keyOne, valueOne, keyFour, valueFour);
                    }
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new TwoContextMap(keyTwo, valueTwo, keyFour, valueFour);
                    }
                    return new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, keyFour, valueFour);
                }
                if ((removeIndexMask & 0x2) == 0x2) {
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new TwoContextMap(keyThree, valueThree, keyFour, valueFour);
                    }
                    return new ThreeContextMap(keyOne, valueOne, keyThree, valueThree, keyFour, valueFour);
                }
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new ThreeContextMap(keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour);
                }
                return new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree,
                        keyFour, valueFour);
            }
            if ((removeIndexMask & 0x8) == 0x8) {
                if ((removeIndexMask & 0x4) == 0x4) {
                    if ((removeIndexMask & 0x2) == 0x2) {
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new OneContextMap(keyFive, valueFive);
                        }
                        return new TwoContextMap(keyOne, valueOne, keyFive, valueFive);
                    }
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new TwoContextMap(keyTwo, valueTwo, keyFive, valueFive);
                    }
                    return new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, keyFive, valueFive);
                }
                if ((removeIndexMask & 0x2) == 0x2) {
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new TwoContextMap(keyThree, valueThree, keyFive, valueFive);
                    }
                    return new ThreeContextMap(keyOne, valueOne, keyThree, valueThree, keyFive, valueFive);
                }
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new ThreeContextMap(keyTwo, valueTwo, keyThree, valueThree, keyFive, valueFive);
                }
                return new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree,
                        keyFive, valueFive);
            }
            if ((removeIndexMask & 0x4) == 0x4) {
                if ((removeIndexMask & 0x2) == 0x2) {
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new TwoContextMap(keyFour, valueFour, keyFive, valueFive);
                    }
                    return new ThreeContextMap(keyOne, valueOne, keyFour, valueFour, keyFive, valueFive);
                }
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new ThreeContextMap(keyTwo, valueTwo, keyFour, valueFour, keyFive, valueFive);
                }
                return new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyFour, valueFour,
                        keyFive, valueFive);
            }
            if ((removeIndexMask & 0x2) == 0x2) {
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new ThreeContextMap(keyThree, valueThree, keyFour, valueFour,
                            keyFive, valueFive);
                }
                return new FourContextMap(keyOne, valueOne, keyThree, valueThree, keyFour, valueFour,
                        keyFive, valueFive);
            }
            if ((removeIndexMask & 0x1) == 0x1) {
                return new FourContextMap(keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour,
                        keyFive, valueFive);
            }
            return null;
        }

        @Nullable
        @Override
        public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
            if (!consumer.test(keyOne, valueOne)) {
                return keyOne;
            }
            if (!consumer.test(keyTwo, valueTwo)) {
                return keyTwo;
            }
            if (!consumer.test(keyThree, valueThree)) {
                return keyThree;
            }
            if (!consumer.test(keyFour, valueFour)) {
                return keyFour;
            }
            return consumer.test(keyFive, valueFive) ? null : keyFive;
        }

        @Override
        public int hashCode() {
            int result = keyOne.hashCode();
            result = 31 * result + Objects.hashCode(valueOne);
            result = 31 * result + keyTwo.hashCode();
            result = 31 * result + Objects.hashCode(valueTwo);
            result = 31 * result + keyThree.hashCode();
            result = 31 * result + Objects.hashCode(valueThree);
            result = 31 * result + keyFour.hashCode();
            result = 31 * result + Objects.hashCode(valueFour);
            result = 31 * result + keyFive.hashCode();
            result = 31 * result + Objects.hashCode(valueFive);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final FiveContextMap that = (FiveContextMap) o;
            return keyOne.equals(that.keyOne) && Objects.equals(valueOne, that.valueOne) &&
                    keyTwo.equals(that.keyTwo) && Objects.equals(valueTwo, that.valueTwo) &&
                    keyThree.equals(that.keyThree) && Objects.equals(valueThree, that.valueThree) &&
                    keyFour.equals(that.keyFour) && Objects.equals(valueFour, that.valueFour) &&
                    keyFive.equals(that.keyFive) && Objects.equals(valueFive, that.valueFive);
        }
    }

    private static final class SixContextMap implements CopyContextMap {

        private final Key<?> keyOne;
        @Nullable
        private final Object valueOne;

        private final Key<?> keyTwo;
        @Nullable
        private final Object valueTwo;

        private final Key<?> keyThree;
        @Nullable
        private final Object valueThree;

        private final Key<?> keyFour;
        @Nullable
        private final Object valueFour;

        private final Key<?> keyFive;
        @Nullable
        private final Object valueFive;

        private final Key<?> keySix;
        @Nullable
        private final Object valueSix;

        SixContextMap(final Key<?> keyOne, @Nullable final Object valueOne,
                      final Key<?> keyTwo, @Nullable final Object valueTwo,
                      final Key<?> keyThree, @Nullable final Object valueThree,
                      final Key<?> keyFour, @Nullable final Object valueFour,
                      final Key<?> keyFive, @Nullable final Object valueFive,
                      final Key<?> keySix, @Nullable final Object valueSix) {
            this.keyOne = keyOne;
            this.valueOne = valueOne;
            this.keyTwo = keyTwo;
            this.valueTwo = valueTwo;
            this.keyThree = keyThree;
            this.valueThree = valueThree;
            this.keyFour = keyFour;
            this.valueFour = valueFour;
            this.keyFive = keyFive;
            this.valueFive = valueFive;
            this.keySix = keySix;
            this.valueSix = valueSix;
        }

        @Override
        public int size() {
            return 6;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean containsKey(final Key<?> key) {
            return key.equals(keyOne) || key.equals(keyTwo) || key.equals(keyThree) || key.equals(keyFour) ||
                    key.equals(keyFive) || key.equals(keySix);
        }

        @Override
        public boolean containsValue(@Nullable final Object value) {
            return Objects.equals(value, valueOne) || Objects.equals(value, valueTwo) ||
                    Objects.equals(value, valueThree) || Objects.equals(value, valueFour) ||
                    Objects.equals(value, valueFive) || Objects.equals(value, valueSix);
        }

        @Override
        public <T> boolean contains(final Key<T> key, @Nullable final T value) {
            return (key.equals(keyOne) && Objects.equals(value, valueOne)) ||
                    (key.equals(keyTwo) && Objects.equals(value, valueTwo)) ||
                    (key.equals(keyThree) && Objects.equals(value, valueThree)) ||
                    (key.equals(keyFour) && Objects.equals(value, valueFour)) ||
                    (key.equals(keyFive) && Objects.equals(value, valueFive)) ||
                    (key.equals(keySix) && Objects.equals(value, valueSix));
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(final Key<T> key) {
            return key.equals(keyOne) ? (T) valueOne : key.equals(keyTwo) ? (T) valueTwo : key.equals(keyThree) ?
                    (T) valueThree : key.equals(keyFour) ? (T) valueFour : key.equals(keyFive) ? (T) valueFive :
                    key.equals(keySix) ? (T) valueSix : null;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
            return key.equals(keyOne) ? (T) valueOne : key.equals(keyTwo) ? (T) valueTwo : key.equals(keyThree) ?
                    (T) valueThree : key.equals(keyFour) ? (T) valueFour : key.equals(keyFive) ? (T) valueFive :
                    key.equals(keySix) ? (T) valueSix : defaultValue;
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T put(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                return mapUpdater.compareAndSet(owner, this, new SixContextMap(key, value, keyTwo, valueTwo,
                        keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                        (T) valueOne : owner.put(key, value);
            }
            if (key.equals(keyTwo)) {
                return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne, key, value,
                        keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                        (T) valueTwo : owner.put(key, value);
            }
            if (key.equals(keyThree)) {
                return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne, keyTwo, valueTwo,
                        key, value, keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                        (T) valueThree : owner.put(key, value);
            }
            if (key.equals(keyFour)) {
                return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne, keyTwo, valueTwo,
                        keyThree, valueThree, key, value, keyFive, valueFive, keySix, valueSix)) ?
                        (T) valueFour : owner.put(key, value);
            }
            if (key.equals(keyFive)) {
                return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne, keyTwo, valueTwo,
                        keyThree, valueThree, keyFour, valueFour, key, value, keySix, valueSix)) ?
                        (T) valueFive : owner.put(key, value);
            }
            if (key.equals(keySix)) {
                return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne, keyTwo, valueTwo,
                        keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, key, value)) ?
                        (T) valueSix : owner.put(key, value);
            }
            return mapUpdater.compareAndSet(owner, this, new SevenOrMoreContextMap(keyOne, valueOne,
                    keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, keySix, valueSix,
                    key, value)) ?
                    null : owner.put(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T putIfAbsent(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                if (valueOne == null) {
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(key, value, keyTwo, valueTwo,
                            keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueOne;
            }
            if (key.equals(keyTwo)) {
                if (valueTwo == null) {
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne, key, value,
                            keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueTwo;
            }
            if (key.equals(keyThree)) {
                if (valueThree == null) {
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, key, value, keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueThree;
            }
            if (key.equals(keyFour)) {
                if (valueFour == null) {
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, key, value, keyFive, valueFive, keySix, valueSix)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueFour;
            }
            if (key.equals(keyFive)) {
                if (valueFive == null) {
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, key, value, keySix, valueSix)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueFive;
            }
            if (key.equals(keySix)) {
                if (valueSix == null) {
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive,
                            key, value)) ?
                            null : owner.putIfAbsent(key, value);
                }
                return (T) valueSix;
            }
            return mapUpdater.compareAndSet(owner, this, new SevenOrMoreContextMap(keyOne, valueOne,
                    keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, keySix, valueSix,
                    key, value)) ?
                    null : owner.putIfAbsent(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction,
                     final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                if (valueOne == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(key, value, keyTwo, valueTwo,
                            keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueOne;
            }
            if (key.equals(keyTwo)) {
                if (valueTwo == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne, key, value,
                            keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueTwo;
            }
            if (key.equals(keyThree)) {
                if (valueThree == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, key, value, keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueThree;
            }
            if (key.equals(keyFour)) {
                if (valueFour == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, key, value, keyFive, valueFive, keySix, valueSix)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueFour;
            }
            if (key.equals(keyFive)) {
                if (valueFive == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, key, value, keySix, valueSix)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueFive;
            }
            if (key.equals(keySix)) {
                if (valueSix == null) {
                    final T value = computeFunction.apply(key);
                    return mapUpdater.compareAndSet(owner, this, new SixContextMap(keyOne, valueOne,
                            keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive,
                            key, value)) ?
                            value : owner.computeIfAbsent(key, computeFunction);
                }
                return (T) valueSix;
            }
            final T value = computeFunction.apply(key);
            return mapUpdater.compareAndSet(owner, this, new SevenOrMoreContextMap(keyOne, valueOne,
                    keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, keySix, valueSix,
                    key, value)) ?
                    value : owner.computeIfAbsent(key, computeFunction);
        }

        @Override
        public CopyContextMap putAll(final int mapSize, final Consumer<PutAllBuilder> forEach) {
            final PutAllBuilder builder = new PutAllBuilder(size() + mapSize);
            builder.addPair(keyOne, valueOne);
            builder.addPair(keyTwo, valueTwo);
            builder.addPair(keyThree, valueThree);
            builder.addPair(keyFour, valueFour);
            builder.addPair(keyFive, valueFive);
            builder.addPair(keySix, valueSix);
            forEach.accept(builder);
            return builder.build();
        }

        @SuppressWarnings("unchecked")
        @Nullable
        @Override
        public <T> T remove(final Key<T> key, final CopyOnWriteContextMap owner,
                        final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            if (key.equals(keyOne)) {
                return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyTwo, valueTwo,
                        keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                        (T) valueOne : owner.remove(key);
            }
            if (key.equals(keyTwo)) {
                return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne,
                        keyThree, valueThree, keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                        (T) valueTwo : owner.remove(key);
            }
            if (key.equals(keyThree)) {
                return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne, keyTwo, valueTwo,
                        keyFour, valueFour, keyFive, valueFive, keySix, valueSix)) ?
                        (T) valueThree : owner.remove(key);
            }
            if (key.equals(keyFour)) {
                return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne, keyTwo, valueTwo,
                        keyThree, valueThree, keyFive, valueFive, keySix, valueSix)) ?
                        (T) valueFour : owner.remove(key);
            }
            if (key.equals(keyFive)) {
                return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne, keyTwo, valueTwo,
                        keyThree, valueThree, keyFour, valueFour, keySix, valueSix)) ?
                        (T) valueFive : owner.remove(key);
            }
            if (key.equals(keySix)) {
                return mapUpdater.compareAndSet(owner, this, new FiveContextMap(keyOne, valueOne, keyTwo, valueTwo,
                        keyThree, valueThree, keyFour, valueFour, keyFive, valueFive)) ?
                        (T) valueSix : owner.remove(key);
            }
            return null;
        }

        @Override
        public boolean removeAll(final Iterable<Key<?>> keys, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            final CopyContextMap newMap = removeAll(removeIndexMask(keys));
            if (newMap == null) {
                return false;
            }
            return mapUpdater.compareAndSet(owner, this, newMap) || owner.removeAll(keys);
        }

        private int removeIndexMask(final Iterable<ContextMap.Key<?>> keys) {
            int mask = 0;
            for (ContextMap.Key<?> k : keys) {
                if (k.equals(keyOne)) {
                    mask |= 0x1;
                } else if (k.equals(keyTwo)) {
                    mask |= 0x2;
                } else if (k.equals(keyThree)) {
                    mask |= 0x4;
                } else if (k.equals(keyFour)) {
                    mask |= 0x8;
                } else if (k.equals(keyFive)) {
                    mask |= 0x10;
                } else if (k.equals(keySix)) {
                    mask |= 0x20;
                }
            }
            return mask;
        }

        @Nullable
        private CopyContextMap removeAll(final int removeIndexMask) {
            if ((removeIndexMask & 0x3F) == 0x3F) {
                return EmptyContextMap.INSTANCE;
            }
            if ((removeIndexMask & 0x20) == 0x20) {
                if ((removeIndexMask & 0x10) == 0x10) {
                    if ((removeIndexMask & 0x8) == 0x8) {
                        if ((removeIndexMask & 0x4) == 0x4) {
                            if ((removeIndexMask & 0x2) == 0x2) {
                                return new OneContextMap(keyOne, valueOne);
                            }
                            if ((removeIndexMask & 0x1) == 0x1) {
                                return new OneContextMap(keyTwo, valueTwo);
                            }
                            return new TwoContextMap(keyOne, valueOne, keyTwo, valueTwo);
                        }
                        if ((removeIndexMask & 0x2) == 0x2) {
                            if ((removeIndexMask & 0x1) == 0x1) {
                                return new OneContextMap(keyThree, valueThree);
                            }
                            return new TwoContextMap(keyOne, valueOne, keyThree, valueThree);
                        }
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new TwoContextMap(keyTwo, valueTwo, keyThree, valueThree);
                        }
                        return new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree);
                    }
                    if ((removeIndexMask & 0x4) == 0x4) {
                        if ((removeIndexMask & 0x2) == 0x2) {
                            if ((removeIndexMask & 0x1) == 0x1) {
                                return new OneContextMap(keyFour, valueFour);
                            }
                            return new TwoContextMap(keyOne, valueOne, keyFour, valueFour);
                        }
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new TwoContextMap(keyTwo, valueTwo, keyFour, valueFour);
                        }
                        return new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, keyFour, valueFour);
                    }
                    if ((removeIndexMask & 0x2) == 0x2) {
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new TwoContextMap(keyThree, valueThree, keyFour, valueFour);
                        }
                        return new ThreeContextMap(keyOne, valueOne, keyThree, valueThree, keyFour, valueFour);
                    }
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new ThreeContextMap(keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour);
                    }
                    return new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree,
                            keyFour, valueFour);
                }
                if ((removeIndexMask & 0x8) == 0x8) {
                    if ((removeIndexMask & 0x4) == 0x4) {
                        if ((removeIndexMask & 0x2) == 0x2) {
                            if ((removeIndexMask & 0x1) == 0x1) {
                                return new OneContextMap(keyFive, valueFive);
                            }
                            return new TwoContextMap(keyOne, valueOne, keyFive, valueFive);
                        }
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new TwoContextMap(keyTwo, valueTwo, keyFive, valueFive);
                        }
                        return new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, keyFive, valueFive);
                    }
                    if ((removeIndexMask & 0x2) == 0x2) {
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new TwoContextMap(keyThree, valueThree, keyFive, valueFive);
                        }
                        return new ThreeContextMap(keyOne, valueOne, keyThree, valueThree, keyFive, valueFive);
                    }
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new ThreeContextMap(keyTwo, valueTwo, keyThree, valueThree, keyFive, valueFive);
                    }
                    return new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree,
                            keyFive, valueFive);
                }
                if ((removeIndexMask & 0x4) == 0x4) {
                    if ((removeIndexMask & 0x2) == 0x2) {
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new TwoContextMap(keyFour, valueFour, keyFive, valueFive);
                        }
                        return new ThreeContextMap(keyOne, valueOne, keyFour, valueFour, keyFive, valueFive);
                    }
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new ThreeContextMap(keyTwo, valueTwo, keyFour, valueFour, keyFive, valueFive);
                    }
                    return new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyFour, valueFour,
                            keyFive, valueFive);
                }
                if ((removeIndexMask & 0x2) == 0x2) {
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new ThreeContextMap(keyThree, valueThree, keyFour, valueFour, keyFive, valueFive);
                    }
                    return new FourContextMap(keyOne, valueOne, keyThree, valueThree, keyFour, valueFour,
                            keyFive, valueFive);
                }
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new FourContextMap(keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour,
                            keyFive, valueFive);
                }
                return new FiveContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree,
                        keyFour, valueFour, keyFive, valueFive);
            }
            if ((removeIndexMask & 0x10) == 0x10) {
                if ((removeIndexMask & 0x8) == 0x8) {
                    if ((removeIndexMask & 0x4) == 0x4) {
                        if ((removeIndexMask & 0x2) == 0x2) {
                            if ((removeIndexMask & 0x1) == 0x1) {
                                return new OneContextMap(keySix, valueSix);
                            }
                            return new TwoContextMap(keyOne, valueOne, keySix, valueSix);
                        }
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new TwoContextMap(keyTwo, valueTwo, keySix, valueSix);
                        }
                        return new ThreeContextMap(keyOne, valueOne, keyTwo, valueTwo, keySix, valueSix);
                    }
                    if ((removeIndexMask & 0x2) == 0x2) {
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new TwoContextMap(keyThree, valueThree, keySix, valueSix);
                        }
                        return new ThreeContextMap(keyOne, valueOne, keyThree, valueThree, keySix, valueSix);
                    }
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new ThreeContextMap(keyTwo, valueTwo, keyThree, valueThree, keySix, valueSix);
                    }
                    return new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree,
                            keySix, valueSix);
                }
                if ((removeIndexMask & 0x4) == 0x4) {
                    if ((removeIndexMask & 0x2) == 0x2) {
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new TwoContextMap(keyFour, valueFour, keySix, valueSix);
                        }
                        return new ThreeContextMap(keyOne, valueOne, keyFour, valueFour, keySix, valueSix);
                    }
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new ThreeContextMap(keyTwo, valueTwo, keyFour, valueFour, keySix, valueSix);
                    }
                    return new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyFour, valueFour,
                            keySix, valueSix);
                }
                if ((removeIndexMask & 0x2) == 0x2) {
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new ThreeContextMap(keyThree, valueThree, keyFour, valueFour, keySix, valueSix);
                    }
                    return new FourContextMap(keyOne, valueOne, keyThree, valueThree, keyFour, valueFour,
                            keySix, valueSix);
                }
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new FourContextMap(keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour,
                            keySix, valueSix);
                }
                return new FiveContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree,
                        keyFour, valueFour, keySix, valueSix);
            }
            if ((removeIndexMask & 0x8) == 0x8) {
                if ((removeIndexMask & 0x4) == 0x4) {
                    if ((removeIndexMask & 0x2) == 0x2) {
                        if ((removeIndexMask & 0x1) == 0x1) {
                            return new TwoContextMap(keyFive, valueFive, keySix, valueSix);
                        }
                        return new ThreeContextMap(keyOne, valueOne, keyFive, valueFive, keySix, valueSix);
                    }
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new ThreeContextMap(keyTwo, valueTwo, keyFive, valueFive, keySix, valueSix);
                    }
                    return new FourContextMap(keyOne, valueOne, keyTwo, valueTwo, keyFive, valueFive,
                            keySix, valueSix);
                }
                if ((removeIndexMask & 0x2) == 0x2) {
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new ThreeContextMap(keyThree, valueThree, keyFive, valueFive, keySix, valueSix);
                    }
                    return new FourContextMap(keyOne, valueOne, keyThree, valueThree, keyFive, valueFive,
                            keySix, valueSix);
                }
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new FourContextMap(keyTwo, valueTwo, keyThree, valueThree, keyFive, valueFive,
                            keySix, valueSix);
                }
                return new FiveContextMap(keyOne, valueOne, keyTwo, valueTwo, keyThree, valueThree,
                        keyFive, valueFive, keySix, valueSix);
            }
            if ((removeIndexMask & 0x4) == 0x4) {
                if ((removeIndexMask & 0x2) == 0x2) {
                    if ((removeIndexMask & 0x1) == 0x1) {
                        return new ThreeContextMap(keyFour, valueFour, keyFive, valueFive, keySix, valueSix);
                    }
                    return new FourContextMap(keyOne, valueOne, keyFour, valueFour, keyFive, valueFive,
                            keySix, valueSix);
                }
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new FourContextMap(keyTwo, valueTwo, keyFour, valueFour, keyFive, valueFive,
                            keySix, valueSix);
                }
                return new FiveContextMap(keyOne, valueOne, keyTwo, valueTwo, keyFour, valueFour,
                        keyFive, valueFive, keySix, valueSix);
            }
            if ((removeIndexMask & 0x2) == 0x2) {
                if ((removeIndexMask & 0x1) == 0x1) {
                    return new FourContextMap(keyThree, valueThree, keyFour, valueFour, keyFive, valueFive,
                            keySix, valueSix);
                }
                return new FiveContextMap(keyOne, valueOne, keyThree, valueThree, keyFour, valueFour,
                        keyFive, valueFive, keySix, valueSix);
            }
            if ((removeIndexMask & 0x1) == 0x1) {
                return new FiveContextMap(keyTwo, valueTwo, keyThree, valueThree, keyFour, valueFour,
                        keyFive, valueFive, keySix, valueSix);
            }
            return null;
        }

        @Nullable
        @Override
        public Key<?> forEach(final BiPredicate<Key<?>, Object> consumer) {
            if (!consumer.test(keyOne, valueOne)) {
                return keyOne;
            }
            if (!consumer.test(keyTwo, valueTwo)) {
                return keyTwo;
            }
            if (!consumer.test(keyThree, valueThree)) {
                return keyThree;
            }
            if (!consumer.test(keyFour, valueFour)) {
                return keyFour;
            }
            if (!consumer.test(keyFive, valueFive)) {
                return keyFive;
            }
            return consumer.test(keySix, valueSix) ? null : keySix;
        }

        @Override
        public int hashCode() {
            int result = keyOne.hashCode();
            result = 31 * result + Objects.hashCode(valueOne);
            result = 31 * result + keyTwo.hashCode();
            result = 31 * result + Objects.hashCode(valueTwo);
            result = 31 * result + keyThree.hashCode();
            result = 31 * result + Objects.hashCode(valueThree);
            result = 31 * result + keyFour.hashCode();
            result = 31 * result + Objects.hashCode(valueFour);
            result = 31 * result + keyFive.hashCode();
            result = 31 * result + Objects.hashCode(valueFive);
            result = 31 * result + keySix.hashCode();
            result = 31 * result + Objects.hashCode(valueSix);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SixContextMap that = (SixContextMap) o;
            return keyOne.equals(that.keyOne) && Objects.equals(valueOne, that.valueOne) &&
                    keyTwo.equals(that.keyTwo) && Objects.equals(valueTwo, that.valueTwo) &&
                    keyThree.equals(that.keyThree) && Objects.equals(valueThree, that.valueThree) &&
                    keyFour.equals(that.keyFour) && Objects.equals(valueFour, that.valueFour) &&
                    keyFive.equals(that.keyFive) && Objects.equals(valueFive, that.valueFive) &&
                    keySix.equals(that.keySix) && Objects.equals(valueSix, that.valueSix);
        }
    }

    private static final class SevenOrMoreContextMap implements CopyContextMap {
        /**
         * Array of <[i] = key, [i+1] = value> pairs.
         */
        private final Object[] context;

        SevenOrMoreContextMap(final Object... context) {
            assert context.length >= 2;
            assert context.length % 2 == 0;
            this.context = context;
        }

        private int findIndex(final Key<?> key) {
            for (int i = 0; i < context.length; i += 2) {
                if (key.equals(context[i])) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int size() {
            return context.length >>> 1;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean containsKey(final Key<?> key) {
            return findIndex(key) >= 0;
        }

        @Override
        public boolean containsValue(@Nullable final Object value) {
            for (int i = 1; i < context.length; i += 2) {
                if (Objects.equals(value, context[i])) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public <T> boolean contains(final Key<T> key, @Nullable final T value) {
            for (int i = 0; i < context.length; i += 2) {
                if (key.equals(context[i]) && Objects.equals(value, context[i + 1])) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T get(final Key<T> key) {
            final int i = findIndex(key);
            return i < 0 ? null : (T) context[i + 1];
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T getOrDefault(final Key<T> key, final T defaultValue) {
            final int i = findIndex(key);
            return i < 0 ? defaultValue : (T) context[i + 1];
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T put(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            final int i = findIndex(key);
            final Object returnValue;
            final Object[] context;
            if (i < 0) {
                returnValue = null;
                context = new Object[this.context.length + 2];
                arraycopy(this.context, 0, context, 0, this.context.length);
                context[this.context.length] = key;
                context[this.context.length + 1] = value;
            } else {
                returnValue = this.context[i + 1];
                context = new Object[this.context.length];
                arraycopy(this.context, 0, context, 0, this.context.length);
                context[i + 1] = value;
            }
            return mapUpdater.compareAndSet(owner, this, new SevenOrMoreContextMap(context)) ?
                    (T) returnValue : owner.put(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T putIfAbsent(final Key<T> key, @Nullable final T value, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            final int i = findIndex(key);
            final Object[] context;
            if (i < 0) {
                context = new Object[this.context.length + 2];
                arraycopy(this.context, 0, context, 0, this.context.length);
                context[this.context.length] = key;
                context[this.context.length + 1] = value;
            } else {
                final Object currentValue = this.context[i + 1];
                if (currentValue != null) {
                    return (T) currentValue;
                }
                context = new Object[this.context.length];
                arraycopy(this.context, 0, context, 0, this.context.length);
                context[i + 1] = value;
            }
            return mapUpdater.compareAndSet(owner, this, new SevenOrMoreContextMap(context)) ?
                    null : owner.putIfAbsent(key, value);
        }

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T> T computeIfAbsent(final Key<T> key, final Function<Key<T>, T> computeFunction,
                     final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            final int i = findIndex(key);
            final T value;
            final Object[] context;
            if (i < 0) {
                value = computeFunction.apply(key);
                context = new Object[this.context.length + 2];
                arraycopy(this.context, 0, context, 0, this.context.length);
                context[this.context.length] = key;
                context[this.context.length + 1] = value;
            } else {
                final Object currentValue = this.context[i + 1];
                if (currentValue != null) {
                    return (T) currentValue;
                }
                value = computeFunction.apply(key);
                context = new Object[this.context.length];
                arraycopy(this.context, 0, context, 0, this.context.length);
                context[i + 1] = value;
            }
            return mapUpdater.compareAndSet(owner, this, new SevenOrMoreContextMap(context)) ?
                    value : owner.computeIfAbsent(key, computeFunction);
        }

        @Override
        public CopyContextMap putAll(final int mapSize, final Consumer<PutAllBuilder> forEach) {
            final PutAllBuilder builder = new PutAllBuilder(size() + mapSize);
            builder.addPairs(this.context);
            forEach.accept(builder);
            return builder.build();
        }

        @Nullable
        @Override
        public <T> T remove(final Key<T> key, final CopyOnWriteContextMap owner,
                        final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            int i = findIndex(key);
            if (i < 0) {
                return null;
            }
            @SuppressWarnings("unchecked")
            final T value = (T) this.context[i + 1];
            if (size() == 7) {
                return mapUpdater.compareAndSet(owner, this, removeBelowSeven(i)) ? value : owner.remove(key);
            }
            final Object[] context = new Object[this.context.length - 2];
            arraycopy(this.context, 0, context, 0, i);
            arraycopy(this.context, i + 2, context, i, this.context.length - i - 2);
            return mapUpdater.compareAndSet(owner, this, new SevenOrMoreContextMap(context)) ?
                    value : owner.remove(key);
        }

        @Override
        public boolean removeAll(final Iterable<Key<?>> keys, final CopyOnWriteContextMap owner,
                     final AtomicReferenceFieldUpdater<CopyOnWriteContextMap, CopyContextMap> mapUpdater) {
            final GrowableIntArray indexesToRemove = new GrowableIntArray(
                    keys instanceof Collection ? ((Collection<Key<?>>) keys).size() : 4);
            for (ContextMap.Key<?> k : keys) {
                final int keyIndex = findIndex(k);
                if (keyIndex >= 0) {
                    indexesToRemove.add(keyIndex);
                }
            }

            final CopyContextMap newMap = removeAll(indexesToRemove);
            if (newMap == null) {
                return false;
            }
            return mapUpdater.compareAndSet(owner, this, newMap) || owner.removeAll(keys);
        }

        @Nullable
        private CopyContextMap removeAll(final GrowableIntArray indexesToRemove) {
            if (size() == indexesToRemove.count()) {
                return EmptyContextMap.INSTANCE;
            }
            if (indexesToRemove.count() == 0) {
                return null;
            }
            if (size() - indexesToRemove.count() < 7) {
                return removeBelowSeven(indexesToRemove);
            }

            final Object[] context = new Object[this.context.length - (indexesToRemove.count() << 1)];
            // Preserve entries that were not found in the first pass
            int newContextIndex = 0;
            for (int i = 0; i < this.context.length; i += 2) {
                if (indexesToRemove.isValueAbsent(i)) {
                    context[newContextIndex++] = this.context[i];
                    context[newContextIndex++] = this.context[i + 1];
                }
            }
            assert newContextIndex == context.length;
            return new SevenOrMoreContextMap(context);
        }

        private CopyContextMap removeBelowSeven(int i) {
            switch (i) {
                case 0:
                    return new SixContextMap((Key<?>) context[2], context[3],
                            (Key<?>) context[4], context[5],
                            (Key<?>) context[6], context[7],
                            (Key<?>) context[8], context[9],
                            (Key<?>) context[10], context[11],
                            (Key<?>) context[12], context[13]);
                case 2:
                    return new SixContextMap((Key<?>) context[0], context[1],
                            (Key<?>) context[4], context[5],
                            (Key<?>) context[6], context[7],
                            (Key<?>) context[8], context[9],
                            (Key<?>) context[10], context[11],
                            (Key<?>) context[12], context[13]);
                case 4:
                    return new SixContextMap((Key<?>) context[0], context[1],
                            (Key<?>) context[2], context[3],
                            (Key<?>) context[6], context[7],
                            (Key<?>) context[8], context[9],
                            (Key<?>) context[10], context[11],
                            (Key<?>) context[12], context[13]);
                case 6:
                    return new SixContextMap((Key<?>) context[0], context[1],
                            (Key<?>) context[2], context[3],
                            (Key<?>) context[4], context[5],
                            (Key<?>) context[8], context[9],
                            (Key<?>) context[10], context[11],
                            (Key<?>) context[12], context[13]);
                case 8:
                    return new SixContextMap((Key<?>) context[0], context[1],
                            (Key<?>) context[2], context[3],
                            (Key<?>) context[4], context[5],
                            (Key<?>) context[6], context[7],
                            (Key<?>) context[10], context[11],
                            (Key<?>) context[12], context[13]);
                case 10:
                    return new SixContextMap((Key<?>) context[0], context[1],
                            (Key<?>) context[2], context[3],
                            (Key<?>) context[4], context[5],
                            (Key<?>) context[6], context[7],
                            (Key<?>) context[8], context[9],
                            (Key<?>) context[12], context[13]);
                case 12:
                    return new SixContextMap((Key<?>) context[0], context[1],
                            (Key<?>) context[2], context[3],
                            (Key<?>) context[4], context[5],
                            (Key<?>) context[6], context[7],
                            (Key<?>) context[8], context[9],
                            (Key<?>) context[10], context[11]);
                default:
                    throw new IllegalStateException("Programming error, unable to remove a key at index=" + i);
            }
        }

        private CopyContextMap removeBelowSeven(final GrowableIntArray indexesToRemove) {
            switch (size() - indexesToRemove.count()) {
                case 1:
                    for (int i = 0; i < this.context.length; i += 2) {
                        if (indexesToRemove.isValueAbsent(i)) {
                            return new OneContextMap((Key<?>) context[i], context[i + 1]);
                        }
                    }
                    break;
                case 2: {
                    int keepI1 = -1;
                    for (int i = 0; i < this.context.length; i += 2) {
                        if (indexesToRemove.isValueAbsent(i)) {
                            if (keepI1 < 0) {
                                keepI1 = i;
                            } else {
                                return new TwoContextMap((Key<?>) context[keepI1], context[keepI1 + 1],
                                        (Key<?>) context[i], context[i + 1]);
                            }
                        }
                    }
                    break;
                }
                case 3: {
                    int keepI1 = -1;
                    int keepI2 = -1;
                    for (int i = 0; i < this.context.length; i += 2) {
                        if (indexesToRemove.isValueAbsent(i)) {
                            if (keepI1 < 0) {
                                keepI1 = i;
                            } else if (keepI2 < 0) {
                                keepI2 = i;
                            } else {
                                return new ThreeContextMap((Key<?>) context[keepI1], context[keepI1 + 1],
                                        (Key<?>) context[keepI2], context[keepI2 + 1],
                                        (Key<?>) context[i], context[i + 1]);
                            }
                        }
                    }
                    break;
                }
                case 4: {
                    int keepI1 = -1;
                    int keepI2 = -1;
                    int keepI3 = -1;
                    for (int i = 0; i < this.context.length; i += 2) {
                        if (indexesToRemove.isValueAbsent(i)) {
                            if (keepI1 < 0) {
                                keepI1 = i;
                            } else if (keepI2 < 0) {
                                keepI2 = i;
                            } else if (keepI3 < 0) {
                                keepI3 = i;
                            } else {
                                return new FourContextMap((Key<?>) context[keepI1], context[keepI1 + 1],
                                        (Key<?>) context[keepI2], context[keepI2 + 1],
                                        (Key<?>) context[keepI3], context[keepI3 + 1],
                                        (Key<?>) context[i], context[i + 1]);
                            }
                        }
                    }
                    break;
                }
                case 5: {
                    int keepI1 = -1;
                    int keepI2 = -1;
                    int keepI3 = -1;
                    int keepI4 = -1;
                    for (int i = 0; i < this.context.length; i += 2) {
                        if (indexesToRemove.isValueAbsent(i)) {
                            if (keepI1 < 0) {
                                keepI1 = i;
                            } else if (keepI2 < 0) {
                                keepI2 = i;
                            } else if (keepI3 < 0) {
                                keepI3 = i;
                            } else if (keepI4 < 0) {
                                keepI4 = i;
                            } else {
                                return new FiveContextMap((Key<?>) context[keepI1], context[keepI1 + 1],
                                        (Key<?>) context[keepI2], context[keepI2 + 1],
                                        (Key<?>) context[keepI3], context[keepI3 + 1],
                                        (Key<?>) context[keepI4], context[keepI4 + 1],
                                        (Key<?>) context[i], context[i + 1]);
                            }
                        }
                    }
                    break;
                }
                case 6: {
                    int keepI1 = -1;
                    int keepI2 = -1;
                    int keepI3 = -1;
                    int keepI4 = -1;
                    int keepI5 = -1;
                    for (int i = 0; i < this.context.length; i += 2) {
                        if (indexesToRemove.isValueAbsent(i)) {
                            if (keepI1 < 0) {
                                keepI1 = i;
                            } else if (keepI2 < 0) {
                                keepI2 = i;
                            } else if (keepI3 < 0) {
                                keepI3 = i;
                            } else if (keepI4 < 0) {
                                keepI4 = i;
                            } else if (keepI5 < 0) {
                                keepI5 = i;
                            } else {
                                return new SixContextMap((Key<?>) context[keepI1], context[keepI1 + 1],
                                        (Key<?>) context[keepI2], context[keepI2 + 1],
                                        (Key<?>) context[keepI3], context[keepI3 + 1],
                                        (Key<?>) context[keepI4], context[keepI4 + 1],
                                        (Key<?>) context[keepI5], context[keepI5 + 1],
                                        (Key<?>) context[i], context[i + 1]);
                            }
                        }
                    }
                    break;
                }
                default:
                    break;
            }
            throw new IllegalStateException("Programming error, unable to reduce from " + size() + " to " +
                    (size() - indexesToRemove.count()));
        }

        @Override
        public Key<?> forEach(BiPredicate<Key<?>, Object> consumer) {
            for (int i = 0; i < context.length; i += 2) {
                final Key<?> key = (Key<?>) context[i];
                if (!consumer.test(key, context[i + 1])) {
                    return key;
                }
            }
            return null;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SevenOrMoreContextMap that = (SevenOrMoreContextMap) o;
            return Arrays.equals(context, that.context);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(context);
        }

        private static final class GrowableIntArray {
            private int[] array;
            private int count;

            GrowableIntArray(final int initialSize) {
                array = new int[initialSize];
            }

            void add(final int value) {
                if (count == array.length) {
                    array = Arrays.copyOf(array, array.length << 1);
                }
                array[count++] = value;
            }

            boolean isValueAbsent(final int value) {
                for (int i = 0; i < count; ++i) {
                    if (array[i] == value) {
                        return false;
                    }
                }
                return true;
            }

            int count() {
                return count;
            }
        }
    }

    private static final class PutAllBuilder implements BiConsumer<Key<?>, Object>, BiPredicate<Key<?>, Object> {
        private final Object[] pairs;
        private int index;

        PutAllBuilder(final int size) {
            assert size > 0;
            pairs = new Object[size << 1];
        }

        void addPairs(final Object[] currentPairs) {
            arraycopy(currentPairs, 0, pairs, 0, currentPairs.length);
            index = currentPairs.length;
        }

        int addPair(final Key<?> key, @Nullable final Object value) {
            assert index <= pairs.length - 2;
            pairs[index] = key;
            pairs[++index] = value;
            return ++index;
        }

        @Override
        public void accept(final Key<?> key, @Nullable final Object value) {
            ensureType(key, value);
            test(key, value);
        }

        @Override
        public boolean test(final Key<?> key, @Nullable final Object value) {
            for (int i = 0; i < index; i += 2) {
                if (key.equals(pairs[i])) {
                    pairs[i + 1] = value;
                    return true;
                }
            }
            return addPair(key, value) < pairs.length;
        }

        CopyContextMap build() {
            assert index % 2 == 0;
            if (index <= 12) {
                switch (index) {
                    case 0:
                        return EmptyContextMap.INSTANCE;
                    case 2:
                        return new OneContextMap((Key<?>) pairs[0], pairs[1]);
                    case 4:
                        return new TwoContextMap((Key<?>) pairs[0], pairs[1], (Key<?>) pairs[2], pairs[3]);
                    case 6:
                        return new ThreeContextMap((Key<?>) pairs[0], pairs[1], (Key<?>) pairs[2], pairs[3],
                                (Key<?>) pairs[4], pairs[5]);
                    case 8:
                        return new FourContextMap((Key<?>) pairs[0], pairs[1], (Key<?>) pairs[2], pairs[3],
                                (Key<?>) pairs[4], pairs[5], (Key<?>) pairs[6], pairs[7]);
                    case 10:
                        return new FiveContextMap((Key<?>) pairs[0], pairs[1], (Key<?>) pairs[2], pairs[3],
                                (Key<?>) pairs[4], pairs[5], (Key<?>) pairs[6], pairs[7], (Key<?>) pairs[8], pairs[9]);
                    case 12:
                        return new SixContextMap((Key<?>) pairs[0], pairs[1], (Key<?>) pairs[2], pairs[3],
                                (Key<?>) pairs[4], pairs[5], (Key<?>) pairs[6], pairs[7], (Key<?>) pairs[8], pairs[9],
                                (Key<?>) pairs[10], pairs[11]);
                    default:
                        throw new IllegalStateException("Unexpected index: " + index +
                                ", (expected an even number from 2 to 12");
                }
            }
            if (index == pairs.length) {
                return new SevenOrMoreContextMap(pairs);
            }
            return new SevenOrMoreContextMap(Arrays.copyOf(pairs, index));
        }
    }
}
