/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.SingleZipper.ZipArg;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.FlowControlUtils;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.ConcurrentUtils.calculateSourceRequested;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

final class PublisherZipper {
    static final int ZIP_MAX_CONCURRENCY = 64;

    private PublisherZipper() {
    }

    @SuppressWarnings("unchecked")
    static <T1, T2, R> Publisher<R> zip(
            boolean delayError, int maxOutstandingDemand, Publisher<? extends T1> p1, Publisher<? extends T2> p2,
            BiFunction<? super T1, ? super T2, ? extends R> zipper) {
        return zip(delayError, maxOutstandingDemand, objects -> zipper.apply((T1) objects[0], (T2) objects[1]), p1, p2);
    }

    @SuppressWarnings("unchecked")
    static <T1, T2, T3, R> Publisher<R> zip(
            boolean delayError, int maxOutstandingDemand, Publisher<? extends T1> p1, Publisher<? extends T2> p2,
            Publisher<? extends T3> p3, Function3<? super T1, ? super T2, ? super T3, ? extends R> zipper) {
        return zip(delayError, maxOutstandingDemand,
                objects -> zipper.apply((T1) objects[0], (T2) objects[1], (T3) objects[2]), p1, p2, p3);
    }

    @SuppressWarnings("unchecked")
    static <T1, T2, T3, T4, R> Publisher<R> zip(
            boolean delayError, int maxOutstandingDemand,
            Publisher<? extends T1> p1, Publisher<? extends T2> p2, Publisher<? extends T3> p3,
            Publisher<? extends T4> p4, Function4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> zipper) {
        return zip(delayError, maxOutstandingDemand,
                objects -> zipper.apply((T1) objects[0], (T2) objects[1], (T3) objects[2], (T4) objects[3]),
                p1, p2, p3, p4);
    }

    static <R> Publisher<R> zip(boolean delayError, int maxOutstandingDemand,
                                Function<? super Object[], ? extends R> zipper, Publisher<?>... publishers) {
        if (maxOutstandingDemand <= 0) {
            throw new IllegalArgumentException("maxOutstandingDemand: " + maxOutstandingDemand + " (expected>0)");
        }
        return defer(() -> {
            // flatMap doesn't require any ordering and so it always optimistically requests from all mapped Publishers
            // as long as there is demand from downstream to ensure forward progress is made if some Publishers aren't
            // producing. However, for the zip use case we queue all signals internally before applying the zipper, and
            // request more from upstream if we don't have a signal from all Publishers. If we let flatMap request
            // unrestricted we could end up queuing infinitely from 1 Publisher while none of the others are producing
            // (or producing much more slowly) and run out of memory. To prevent this issue we limit upstream demand
            // to each Publisher until we can deliver a zipped result downstream which means the zip queues are bounded.
            @SuppressWarnings("unchecked")
            RequestLimiterSubscriber<ZipArg>[] demandSubscribers = (RequestLimiterSubscriber<ZipArg>[])
                    Array.newInstance(RequestLimiterSubscriber.class, publishers.length);
            @SuppressWarnings("unchecked")
            Publisher<ZipArg>[] mappedPublishers = new Publisher[publishers.length];
            for (int i = 0; i < publishers.length; ++i) {
                final int finalI = i;
                mappedPublishers[i] = publishers[i].map(v -> new ZipArg(finalI, v)).liftSync(subscriber -> {
                    RequestLimiterSubscriber<ZipArg> demandSubscriber =
                            new RequestLimiterSubscriber<>(subscriber, maxOutstandingDemand);
                    demandSubscribers[finalI] = demandSubscriber;
                    return demandSubscriber;
                });
            }

            return (delayError ? from(mappedPublishers)
                        .flatMapMergeDelayError(identity(), mappedPublishers.length, mappedPublishers.length) :
                    from(mappedPublishers).flatMapMerge(identity(), mappedPublishers.length))
                    .liftSync(new ZipPublisherOperator<>(mappedPublishers.length, zipper, demandSubscribers))
                    .shareContextOnSubscribe();
        });
    }

    private static final class ZipPublisherOperator<R> implements PublisherOperator<ZipArg, R> {
        private final int zipperArity;
        private final Function<? super Object[], ? extends R> zipper;
        private final RequestLimiterSubscriber<?>[] demandSubscribers;

        private ZipPublisherOperator(final int zipperArity, final Function<? super Object[], ? extends R> zipper,
                                     final RequestLimiterSubscriber<?>[] demandSubscribers) {
            if (zipperArity > 64 || zipperArity <= 0) { // long used as bit mask to check for non-empty queues.
                throw new IllegalArgumentException("zipperArity " + zipperArity + "(expected: <64 && >0)");
            }
            this.zipperArity = zipperArity;
            this.zipper = requireNonNull(zipper);
            this.demandSubscribers = requireNonNull(demandSubscribers);
        }

        @Override
        public Subscriber<? super ZipArg> apply(final Subscriber<? super R> subscriber) {
            return new ZipSubscriber<>(subscriber, zipperArity, zipper, demandSubscribers);
        }

        private static final class ZipSubscriber<R> implements Subscriber<ZipArg> {
            private static final long ALL_NON_EMPTY_MASK = 0xFFFFFFFFFFFFFFFFL;
            private final Subscriber<? super R> subscriber;
            private final Queue<Object>[] array;
            private final Function<? super Object[], ? extends R> zipper;
            private final RequestLimiterSubscriber<?>[] demandSubscribers;
            private long nonEmptyQueueIndexes;
            @Nullable
            private Subscription subscription;

            @SuppressWarnings("unchecked")
            private ZipSubscriber(final Subscriber<? super R> subscriber,
                                  final int zipperArity,
                                  final Function<? super Object[], ? extends R> zipper,
                                  final RequestLimiterSubscriber<?>[] demandSubscribers) {
                this.subscriber = subscriber;
                array = (Queue<Object>[]) Array.newInstance(Queue.class, zipperArity);
                for (int i = 0; i < zipperArity; ++i) {
                    array[i] = new ArrayDeque<>();
                }
                this.demandSubscribers = requireNonNull(demandSubscribers);
                this.zipper = requireNonNull(zipper);
                for (int i = 63; i >= zipperArity; --i) {
                    nonEmptyQueueIndexes |= (1L << i);
                }
            }

            @Override
            public void onSubscribe(final Subscription s) {
                this.subscription = ConcurrentSubscription.wrap(s);
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(@Nullable final ZipArg zipArg) {
                assert zipArg != null;
                array[zipArg.index].add(wrapNull(zipArg.value));
                nonEmptyQueueIndexes |= 1L << zipArg.index;
                if (nonEmptyQueueIndexes == ALL_NON_EMPTY_MASK) {
                    Object[] zipArray = new Object[array.length];
                    for (int i = 0; i < array.length; ++i) {
                        final Queue<Object> arrayQueue = array[i];
                        final Object queuePoll = arrayQueue.poll();
                        assert queuePoll != null;
                        if (arrayQueue.isEmpty()) {
                            nonEmptyQueueIndexes &= ~(1L << i);
                        }
                        zipArray[i] = unwrapNullUnchecked(queuePoll);
                        // Allow this subscriber to request more if demand is pending.
                        // Reentry: note that we call out to request more before we dequeued the current set of signals
                        // which in theory may result in out of order delivery. However, flatMap protects against
                        // reentry so no need to provide double protection in this method.
                        demandSubscribers[i].incrementSourceEmitted();
                    }
                    subscriber.onNext(zipper.apply(zipArray));
                } else {
                    assert subscription != null;
                    subscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                List<Integer> nonEmptyIndexes = new ArrayList<>();
                for (int i = 0; i < array.length; ++i) {
                    if ((nonEmptyQueueIndexes & (1L << i)) != 0) {
                        nonEmptyIndexes.add(i);
                    }
                }
                if (nonEmptyIndexes.isEmpty()) {
                    subscriber.onComplete();
                } else {
                    StringBuilder sb = new StringBuilder(20 + 68 + nonEmptyIndexes.size() * 4);
                    sb.append("Publisher indexes: [");
                    Iterator<Integer> itr = nonEmptyIndexes.iterator();
                    sb.append(itr.next()); // safe to call next(), already checked is not empty.
                    while (itr.hasNext()) {
                        sb.append(", ").append(itr.next());
                    }
                    sb.append("] had onNext signals queued when onComplete terminal signal received");
                    subscriber.onError(new IllegalStateException(sb.toString()));
                }
            }
        }
    }

    /**
     * Limits the outstanding demand upstream to a positive {@link Integer} value.
     * @param <T> The type of data.
     */
    private static final class RequestLimiterSubscriber<T> implements Subscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<RequestLimiterSubscriber> sourceEmittedUpdater =
                AtomicLongFieldUpdater.newUpdater(RequestLimiterSubscriber.class, "sourceEmitted");
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<RequestLimiterSubscriber> sourceRequestedUpdater =
                AtomicLongFieldUpdater.newUpdater(RequestLimiterSubscriber.class, "sourceRequested");
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<RequestLimiterSubscriber> requestedUpdater =
                AtomicLongFieldUpdater.newUpdater(RequestLimiterSubscriber.class, "requested");
        private final Subscriber<? super T> subscriber;
        private volatile long sourceEmitted;
        @SuppressWarnings("unused")
        private volatile long sourceRequested;
        private volatile long requested;
        private final int maxConcurrency;
        @Nullable
        private Subscription subscription;

        RequestLimiterSubscriber(final Subscriber<? super T> subscriber,
                                 final int maxConcurrency) {
            this.subscriber = subscriber;
            this.maxConcurrency = maxConcurrency;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            this.subscription = ConcurrentSubscription.wrap(s);
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                    if (isRequestNValid(n)) {
                        requestedUpdater.accumulateAndGet(RequestLimiterSubscriber.this, n,
                                FlowControlUtils::addWithOverflowProtection);
                        recalculateDemand();
                    } else {
                        subscription.request(n);
                    }
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                }
            });
        }

        @Override
        public void onNext(@Nullable final T t) {
            subscriber.onNext(t);
        }

        @Override
        public void onError(final Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        void incrementSourceEmitted() {
            sourceEmittedUpdater.incrementAndGet(this);
            recalculateDemand();
        }

        private void recalculateDemand() {
            final long actualSourceRequestN = calculateSourceRequested(requestedUpdater, sourceRequestedUpdater,
                    sourceEmittedUpdater, maxConcurrency, this);
            if (actualSourceRequestN != 0) {
                assert subscription != null;
                subscription.request(actualSourceRequestN);
            }
        }
    }
}
