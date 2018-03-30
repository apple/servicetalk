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

import org.reactivestreams.Subscriber;

import java.util.Iterator;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Operator that may map each {@link Subscriber#onNext(Object)} call to multiple {@link Key}s.
 * @param <Key> The type of key which identifies a {@link Group}.
 * @param <T> Type of elements emitted by the {@link Group}s emitted by this {@link Publisher}.
 */
final class PublisherGroupByMulti<Key, T> extends AbstractPublisherGroupBy<Key, T> {
    private final Function<T, Iterator<Key>> keySelector;

    PublisherGroupByMulti(Publisher<T> original, Function<T, Iterator<Key>> keySelector, int groupQueueSize, Executor executor) {
        super(original, groupQueueSize, executor);
        this.keySelector = requireNonNull(keySelector);
    }

    PublisherGroupByMulti(Publisher<T> original, Function<T, Iterator<Key>> keySelector, int groupQueueSize, int expectedGroupCountHint,
                          Executor executor) {
        super(original, groupQueueSize, expectedGroupCountHint, executor);
        this.keySelector = requireNonNull(keySelector);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super Group<Key, T>> subscriber) {
        return new SourceSubscriber<>(this, subscriber);
    }

    private static final class SourceSubscriber<Key, T> extends AbstractSourceSubscriber<Key, T> {
        private final PublisherGroupByMulti<Key, T> source;

        SourceSubscriber(PublisherGroupByMulti<Key, T> source, Subscriber<? super Group<Key, T>> target) {
            super(source.initialCapacityForGroups, target);
            this.source = source;
        }

        @Override
        void onNext0(@Nullable T t) {
            final Iterator<Key> keys;
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
