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
package io.servicetalk.concurrent;

import java.util.Spliterator;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

final class SpliteratorToBlockingSpliterator<T> implements BlockingSpliterator<T> {
    private final Spliterator<T> spliterator;
    private final AutoCloseable closable;

    SpliteratorToBlockingSpliterator(AutoCloseable closable, Spliterator<T> spliterator) {
        this.spliterator = requireNonNull(spliterator);
        this.closable = requireNonNull(closable);
    }

    @Override
    public boolean tryAdvance(final Consumer<? super T> action) {
        return spliterator.tryAdvance(action);
    }

    @Override
    public BlockingSpliterator<T> trySplit() {
        return new SpliteratorToBlockingSpliterator<>(closable, spliterator.trySplit());
    }

    @Override
    public long estimateSize() {
        return spliterator.estimateSize();
    }

    @Override
    public int characteristics() {
        return spliterator.characteristics();
    }

    @Override
    public void close() throws Exception {
        closable.close();
    }
}
