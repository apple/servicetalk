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
package io.servicetalk.concurrent.api;

import java.util.Iterator;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Operator that may map each {@link Subscriber#onNext(Object)} call to multiple {@link Key}s.
 * @param <Key> The type of key which identifies a {@link GroupedPublisher}.
 * @param <T> Type of elements emitted by the {@link GroupedPublisher}s emitted by this {@link Publisher}.
 */
final class PublisherGroupToMany<Key, T> extends AbstractPublisherGroupBy<Key, T> {
    private final Function<? super T, ? extends Iterator<? extends Key>> keySelector;

    PublisherGroupToMany(Publisher<T> original, Function<? super T, ? extends Iterator<? extends Key>> keySelector,
                         int groupQueueSize) {
        super(original, groupQueueSize);
        this.keySelector = requireNonNull(keySelector);
    }

    PublisherGroupToMany(Publisher<T> original, Function<? super T, ? extends Iterator<? extends Key>> keySelector,
                         int groupQueueSize, int expectedGroupCountHint) {
        super(original, groupQueueSize, expectedGroupCountHint);
        this.keySelector = requireNonNull(keySelector);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super GroupedPublisher<Key, T>> subscriber) {
        return new SourceSubscriber<>(executor(), this, subscriber);
    }

    private static final class SourceSubscriber<Key, T> extends AbstractSourceSubscriber<Key, T> {
        private final PublisherGroupToMany<Key, T> source;

        SourceSubscriber(Executor executor, PublisherGroupToMany<Key, T> source,
                         Subscriber<? super GroupedPublisher<Key, T>> target) {
            super(executor, source.initialCapacityForGroups, target);
            this.source = source;
        }

        @Override
        void onNext0(@Nullable T t) {
            final Iterator<? extends Key> keys;
            try {
                keys = requireNonNull(source.keySelector.apply(t));
            } catch (Throwable throwable) {
                cancelSourceFromSource(false, throwable);
                return;
            }
            keys.forEachRemaining(key -> onNextGroup(key, t));
        }

        @Override
        int groupQueueSize() {
            return source.groupQueueSize;
        }
    }
}
