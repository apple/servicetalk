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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkTerminationValidWithConcurrentOnNextCheck;
import static io.servicetalk.concurrent.internal.SubscriberUtils.sendOnNextWithConcurrentTerminationCheck;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static io.servicetalk.transport.api.FlushStrategyHolder.from;
import static java.util.Objects.requireNonNull;

final class BatchFlush implements FlushStrategy {

    private final Publisher<?> durationBoundaries;
    private final int batchSize;

    BatchFlush(Publisher<?> durationBoundaries, int batchSize) {
        this.durationBoundaries = requireNonNull(durationBoundaries);
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize: " + batchSize + " (expected > 0)");
        }
        this.batchSize = batchSize;
    }

    @Override
    public <T> FlushStrategyHolder<T> apply(Publisher<T> source, Executor executor) {
        requireNonNull(source);
        FlushStrategyHolder.FlushSignals signals = new FlushStrategyHolder.FlushSignals();
        Publisher<T> src = new Publisher<T>(executor) {
            @Override
            protected void handleSubscribe(Subscriber<? super T> subscriber) {
                source.subscribe(new MultiSourceBatchSubscriber<>(subscriber, durationBoundaries, signals, batchSize));
            }
        };
        return from(src, signals);
    }

    static final class MultiSourceBatchSubscriber<T> implements Subscriber<T> {

        private static final AtomicIntegerFieldUpdater<MultiSourceBatchSubscriber> unflushedCountUpdater =
                AtomicIntegerFieldUpdater.newUpdater(MultiSourceBatchSubscriber.class, "unflushedCount");
        private static final AtomicIntegerFieldUpdater<MultiSourceBatchSubscriber> subscriberStateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(MultiSourceBatchSubscriber.class, "subscriberState");
        private static final AtomicReferenceFieldUpdater<MultiSourceBatchSubscriber, TerminalNotification> terminalNotificationUpdater =
                AtomicReferenceFieldUpdater.newUpdater(MultiSourceBatchSubscriber.class, TerminalNotification.class, "terminalNotification");

        private final Subscriber<? super T> subscriber;
        private final Publisher<?> durationBoundaries;
        private final FlushStrategyHolder.FlushSignals signals;
        private final int batchSize;

        @Nullable
        private volatile Subscription durationSubscription;
        @SuppressWarnings("unused")
        private volatile int unflushedCount;
        @SuppressWarnings("unused")
        private volatile int subscriberState;
        @Nullable
        @SuppressWarnings("unused")
        private volatile TerminalNotification terminalNotification;

        MultiSourceBatchSubscriber(Subscriber<? super T> subscriber, Publisher<?> durationBoundaries, FlushStrategyHolder.FlushSignals signals,
                                   int batchSize) {
            this.subscriber = requireNonNull(subscriber);
            this.durationBoundaries = durationBoundaries;
            this.signals = signals;
            this.batchSize = batchSize;
        }

        @Override
        public void onSubscribe(Subscription actualSubscription) {
            durationBoundaries.subscribe(new Subscriber<Object>() {
                @Override
                public void onSubscribe(Subscription s) {
                    if (checkDuplicateSubscription(durationSubscription, s)) {
                        durationSubscription = requireNonNull(s);
                        subscriber.onSubscribe(actualSubscription);
                        s.request(1);
                    }
                }

                @Override
                public void onNext(Object o) {
                    final Subscription ds = getDurationSubscriptionOrDie();
                    sendFlush();
                    ds.request(1);
                }

                @Override
                public void onError(Throwable t) {
                    TerminalNotification terminalNotification = error(t);
                    if (checkTerminationValidWithConcurrentOnNextCheck(null, terminalNotification, subscriberStateUpdater, terminalNotificationUpdater, MultiSourceBatchSubscriber.this)) {
                        terminate(terminalNotification, actualSubscription);
                    }
                }

                @Override
                public void onComplete() {
                    // No more time based flushes.
                }
            });
        }

        @Override
        public void onNext(T t) {
            sendOnNextWithConcurrentTerminationCheck(() -> {
                        subscriber.onNext(t);
                        int unflushed = unflushedCountUpdater.incrementAndGet(this);
                        if (shouldFlush(unflushed, batchSize)) {
                            sendFlush();
                        }
                    }, terminalNotification -> terminate(terminalNotification, getDurationSubscriptionOrDie()),
                    subscriberStateUpdater, terminalNotificationUpdater, this);
        }

        @Override
        public void onError(Throwable t) {
            TerminalNotification terminalNotification = error(t);
            if (checkTerminationValidWithConcurrentOnNextCheck(null, terminalNotification, subscriberStateUpdater, terminalNotificationUpdater, this)) {
                terminate(terminalNotification, getDurationSubscriptionOrDie());
            }
        }

        @Override
        public void onComplete() {
            TerminalNotification terminalNotification = complete();
            if (checkTerminationValidWithConcurrentOnNextCheck(null, terminalNotification, subscriberStateUpdater, terminalNotificationUpdater, this)) {
                terminate(terminalNotification, getDurationSubscriptionOrDie());
            }
        }

        /**
         * Determines whether the number of writes pending flush should be flushed.
         *
         * @param unflushedWrites Number of writes which have not been flushed.
         * @param batchSize Configured batch size for this {@link MultiSourceBatchSubscriber}.
         *
         * @return {@code true} if the writes should be flushed.
         */
        boolean shouldFlush(int unflushedWrites, int batchSize) {
            return unflushedWrites == batchSize;
        }

        /**
         * Sends a flush signal, using configured {@link FlushStrategyHolder.FlushSignals#signalFlush()}.
         */
        void sendFlush() {
            int oldUnflushedCount = unflushedCountUpdater.getAndSet(this, 0);
            if (oldUnflushedCount > 0) {
                signals.signalFlush();
            }
        }

        private void terminate(TerminalNotification notification, Subscription toCancel) {
            toCancel.cancel();
            sendFlush();
            notification.terminate(subscriber);
        }

        private Subscription getDurationSubscriptionOrDie() {
            Subscription ds = durationSubscription;
            assert ds != null : "Subscription can not be null.";
            return ds;
        }
    }
}
