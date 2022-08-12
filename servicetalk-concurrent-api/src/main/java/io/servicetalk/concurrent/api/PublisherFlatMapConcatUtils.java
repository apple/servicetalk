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
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.releaseLock;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.tryAcquireLock;
import static io.servicetalk.utils.internal.PlatformDependent.newUnboundedSpscQueue;
import static java.lang.Math.min;

final class PublisherFlatMapConcatUtils {
    private PublisherFlatMapConcatUtils() {
    }

    static <T, R> Publisher<R> flatMapConcatSingle(final Publisher<T> publisher,
                                                   final Function<? super T, ? extends Single<? extends R>> mapper) {
        return defer(() -> publisher.flatMapMergeSingle(new OrderedMapper<>(mapper, newUnboundedSpscQueue(4)))
                .shareContextOnSubscribe());
    }

    static <T, R> Publisher<R> flatMapConcatSingleDelayError(
            final Publisher<T> publisher, final Function<? super T, ? extends Single<? extends R>> mapper) {
        return defer(() -> publisher.flatMapMergeSingleDelayError(new OrderedMapper<>(mapper, newUnboundedSpscQueue(4)))
                .shareContextOnSubscribe());
    }

    static <T, R> Publisher<R> flatMapConcatSingle(final Publisher<T> publisher,
                                                   final Function<? super T, ? extends Single<? extends R>> mapper,
                                                   final int maxConcurrency) {
        return defer(() ->
                publisher.flatMapMergeSingle(new OrderedMapper<>(mapper,
                                newUnboundedSpscQueue(min(8, maxConcurrency))), maxConcurrency)
                        .shareContextOnSubscribe());
    }

    static <T, R> Publisher<R> flatMapConcatSingleDelayError(
            final Publisher<T> publisher, final Function<? super T, ? extends Single<? extends R>> mapper,
            final int maxConcurrency) {
        return defer(() ->
                publisher.flatMapMergeSingleDelayError(new OrderedMapper<>(mapper,
                                newUnboundedSpscQueue(min(8, maxConcurrency))), maxConcurrency)
                        .shareContextOnSubscribe());
    }

    private static final class OrderedMapper<T, R> implements Function<T, Single<R>> {
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<OrderedMapper> consumerLockUpdater =
                AtomicIntegerFieldUpdater.newUpdater(OrderedMapper.class, "consumerLock");
        private final Function<? super T, ? extends Single<? extends R>> mapper;
        private final Queue<Item<R>> results;
        @SuppressWarnings("unused")
        private volatile int consumerLock;

        private OrderedMapper(final Function<? super T, ? extends Single<? extends R>> mapper,
                              final Queue<Item<R>> results) {
            this.mapper = mapper;
            this.results = results;
        }

        @Override
        public Single<R> apply(final T t) {
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
                            item.onSuccess(result);
                            tryPollQueue();
                        }

                        @Override
                        public void onError(final Throwable t) {
                            item.onError(t);
                            tryPollQueue();
                        }

                        private void tryPollQueue() {
                            boolean tryAcquire = true;
                            while (tryAcquire && tryAcquireLock(consumerLockUpdater, OrderedMapper.this)) {
                                try {
                                    Item<R> i;
                                    while ((i = results.peek()) != null && i.tryTerminate()) {
                                        results.poll();
                                    }
                                    // flatMapMergeSingle takes care of exception propagation / cleanup
                                } finally {
                                    tryAcquire = !releaseLock(consumerLockUpdater, OrderedMapper.this);
                                }
                            }
                        }
                    });
                }
            }
            // The inner Single will determine if a copy is justified when we subscribe to it.
            .shareContextOnSubscribe();
        }
    }

    private static final class Item<R> {
        @Nullable
        SingleSource.Subscriber<? super R> subscriber;
        @Nullable
        private Object result;

        void onError(Throwable cause) {
            result = new ThrowableWrapper(cause);
        }

        void onSuccess(@Nullable R r) {
            result = wrapNull(r);
        }

        boolean tryTerminate() {
            final Object localResult = result;
            if (localResult == null) {
                return false;
            } else if (ThrowableWrapper.class.equals(localResult.getClass())) {
                assert subscriber != null; // if terminated, must have a subscriber
                subscriber.onError(((ThrowableWrapper) localResult).unwrap());
            } else {
                assert subscriber != null; // if terminated, must have a subscriber
                subscriber.onSuccess(unwrapNullUnchecked(localResult));
            }
            return true;
        }
    }
}
