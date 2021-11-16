/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.benchmark.concurrent;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.context.api.ContextMap.Key;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static java.util.Collections.unmodifiableSet;

@Fork(2)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 2)
@Measurement(iterations = 10, time = 2)
public class AsyncContextMapBenchmark {
    private static final Key<String> K1 = newKey("k1", String.class);
    private static final Key<String> K2 = newKey("k2", String.class);
    private static final Key<String> K3 = newKey("k3", String.class);
    private static final Key<String> K4 = newKey("k4", String.class);
    private static final Key<String> K5 = newKey("k5", String.class);
    private static final Key<String> K6 = newKey("k6", String.class);
    private static final Key<String> K7 = newKey("k7", String.class);
    private static final Key<String> K8 = newKey("k8", String.class);

    @Setup(Level.Invocation)
    public final void setup() {
        AsyncContext.clear();
    }

    @Benchmark
    public void putGetOne() {
        AsyncContext.put(K1, "v1");
        AsyncContext.get(K1);
    }

    @Benchmark
    public void putGetTwo() {
        AsyncContext.put(K1, "v1");
        AsyncContext.put(K2, "v2");
        AsyncContext.get(K1);
        AsyncContext.get(K2);
    }

    @Benchmark
    public void putGetThree() {
        AsyncContext.put(K1, "v1");
        AsyncContext.put(K2, "v2");
        AsyncContext.put(K3, "v3");
        AsyncContext.get(K1);
        AsyncContext.get(K2);
        AsyncContext.get(K3);
    }

    @Benchmark
    public void putGetEight() {
        AsyncContext.put(K1, "v1");
        AsyncContext.put(K2, "v2");
        AsyncContext.put(K3, "v3");
        AsyncContext.put(K4, "v4");
        AsyncContext.put(K5, "v5");
        AsyncContext.put(K6, "v6");
        AsyncContext.put(K7, "v7");
        AsyncContext.put(K8, "v8");
        AsyncContext.get(K1);
        AsyncContext.get(K2);
        AsyncContext.get(K3);
        AsyncContext.get(K4);
        AsyncContext.get(K5);
        AsyncContext.get(K6);
        AsyncContext.get(K7);
        AsyncContext.get(K8);
    }

    @Benchmark
    public void putGetMultiFour() {
        AsyncContext.putAllFromMap(FourMap.INSTANCE);
        AsyncContext.removeAllEntries(FourList.INSTANCE);
    }

    private static final class FourList extends AbstractList<Key<?>> {
        static final List<Key<?>> INSTANCE = new FourList();

        private FourList() {
            // singleton
        }

        @Override
        public Key<?> get(final int index) {
            switch (index) {
                case 0: return K1;
                case 1: return K2;
                case 2: return K3;
                case 3: return K4;
                default: throw new IndexOutOfBoundsException("index: " + index);
            }
        }

        @Override
        public int size() {
            return 4;
        }

        @Override
        public void forEach(Consumer<? super Key<?>> consumer) {
            consumer.accept(K1);
            consumer.accept(K2);
            consumer.accept(K3);
            consumer.accept(K4);
        }
    }

    private static final class FourMap extends AbstractMap<Key<?>, Object> {
        static final Map<Key<?>, Object> INSTANCE = new FourMap();
        private final Set<Entry<Key<?>, Object>> entrySet;

        private FourMap() {
            Set<Entry<Key<?>, Object>> entrySet = new HashSet<>();
            entrySet.add(new SimpleImmutableEntry<>(K1, "v1"));
            entrySet.add(new SimpleImmutableEntry<>(K2, "v2"));
            entrySet.add(new SimpleImmutableEntry<>(K3, "v3"));
            entrySet.add(new SimpleImmutableEntry<>(K4, "v4"));
            this.entrySet = unmodifiableSet(entrySet);
        }

        @Override
        public Set<Entry<Key<?>, Object>> entrySet() {
            return entrySet;
        }

        @Override
        public void forEach(BiConsumer<? super Key<?>, ? super Object> consumer) {
            consumer.accept(K1, "v1");
            consumer.accept(K2, "v2");
            consumer.accept(K3, "v3");
            consumer.accept(K4, "v4");
        }
    }
}
