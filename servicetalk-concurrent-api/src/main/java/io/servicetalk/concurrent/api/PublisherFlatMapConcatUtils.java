/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.utils.internal.PlatformDependent.newUnboundedSpscQueue;
import static java.lang.Math.min;

final class PublisherFlatMapConcatUtils {
    private PublisherFlatMapConcatUtils() {
    }

    static <T, R> Publisher<R> flatMapConcatSingle(final Publisher<T> publisher,
                                                   final Function<? super T, ? extends Single<? extends R>> mapper) {
        return defer(() -> {
            final Queue<Item<R>> results = newUnboundedSpscQueue(4);
            final AtomicInteger consumerLock = new AtomicInteger();
            return publisher.flatMapMergeSingle(orderedMapper(mapper, results, consumerLock))
                    .shareContextOnSubscribe();
        });
    }

    static <T, R> Publisher<R> flatMapConcatSingleDelayError(
            final Publisher<T> publisher, final Function<? super T, ? extends Single<? extends R>> mapper) {
        return defer(() -> {
            final Queue<Item<R>> results = newUnboundedSpscQueue(4);
            final AtomicInteger consumerLock = new AtomicInteger();
            return publisher.flatMapMergeSingleDelayError(orderedMapper(mapper, results, consumerLock))
                    .shareContextOnSubscribe();
        });
    }

    static <T, R> Publisher<R> flatMapConcatSingle(final Publisher<T> publisher,
                                                   final Function<? super T, ? extends Single<? extends R>> mapper,
                                                   final int maxConcurrency) {
        return defer(() -> {
            final Queue<Item<R>> results = newUnboundedSpscQueue(min(8, maxConcurrency));
            final AtomicInteger consumerLock = new AtomicInteger();
            return publisher.flatMapMergeSingle(orderedMapper(mapper, results, consumerLock), maxConcurrency)
                    .shareContextOnSubscribe();
        });
    }

    static <T, R> Publisher<R> flatMapConcatSingleDelayError(
            final Publisher<T> publisher, final Function<? super T, ? extends Single<? extends R>> mapper,
            final int maxConcurrency) {
        return defer(() -> {
            final Queue<Item<R>> results = newUnboundedSpscQueue(min(8, maxConcurrency));
            final AtomicInteger consumerLock = new AtomicInteger();
            return publisher.flatMapMergeSingleDelayError(orderedMapper(mapper, results, consumerLock), maxConcurrency)
                    .shareContextOnSubscribe();
        });
    }

    private static <T, R> Function<? super T, Single<? extends R>> orderedMapper(
            final Function<? super T, ? extends Single<? extends R>> mapper,
            final Queue<Item<R>> results, final AtomicInteger consumerLock) {
        return t -> {
            final Single<? extends R> single = mapper.apply(t);
            final Item<R> item = new Item<>();
            results.add(item);
            return new Single<R>() {
                @Override
                protected void handleSubscribe(final SingleSource.Subscriber<? super R> subscriber) {
                    assert item.subscriber == null; // flatMapMergeSingle only does a single subscribe.
                    item.subscriber = subscriber;
                    toSource(single).subscribe(new SingleSource.Subscriber<R>() {
                        @Override
                        public void onSubscribe(final Cancellable cancellable) {
                            subscriber.onSubscribe(cancellable);
                        }

                        @Override
                        public void onSuccess(@Nullable final R result) {
                            item.result = result;
                            item.terminated = true;
                            tryPollQueue();
                        }

                        @Override
                        public void onError(final Throwable t) {
                            item.cause = t;
                            item.terminated = true;
                            tryPollQueue();
                        }

                        private void tryPollQueue() {
                            boolean tryAcquire = true;
                            while (tryAcquire && tryAcquireLock(consumerLock)) {
                                try {
                                    Item<R> i;
                                    while ((i = results.peek()) != null && i.terminated) {
                                        results.poll();
                                        assert i.subscriber != null; // if terminated, must have a subscriber
                                        if (i.cause != null) {
                                            i.subscriber.onError(i.cause);
                                        } else {
                                            i.subscriber.onSuccess(i.result);
                                        }
                                    }
                                    // flatMapMergeSingle takes care of exception propagation / cleanup
                                } finally {
                                    tryAcquire = !releaseLock(consumerLock);
                                }
                            }
                        }
                    });
                }
            }
            // The inner Single will determine if a copy is justified when we subscribe to it.
            .shareContextOnSubscribe();
        };
    }

    private static final class Item<R> {
        @Nullable
        SingleSource.Subscriber<? super R> subscriber;
        @Nullable
        R result;
        @Nullable
        Throwable cause;
        boolean terminated;
    }
}
