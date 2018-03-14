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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.FlushStrategy;
import io.servicetalk.transport.api.FlushStrategyHolder;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static java.util.Objects.requireNonNull;

final class FlushOnReadCompleteStrategy implements FlushStrategy {

    private final int maxPendingWrites;

    FlushOnReadCompleteStrategy(int maxPendingWrites) {
        if (maxPendingWrites <= 0) {
            throw new IllegalArgumentException("maxPendingWrites: " + maxPendingWrites + " (expected > 0)");
        }
        this.maxPendingWrites = maxPendingWrites;
    }

    @Override
    public <T> FlushStrategyHolder<T> apply(Publisher<T> source) {
        requireNonNull(source);
        FlushStrategyHolder.FlushSignals signals = new FlushStrategyHolder.FlushSignals();
        return new ReadAwareFlushStrategyHolderImpl<>(source, signals, maxPendingWrites);
    }

    private static class ReadAwareFlushStrategyHolderImpl<T> implements ReadAwareFlushStrategyHolder<T> {

        private static final AtomicReferenceFieldUpdater<ReadAwareFlushStrategyHolderImpl, ReadAwareSubscriber> subscriberUpdater =
                AtomicReferenceFieldUpdater.newUpdater(ReadAwareFlushStrategyHolderImpl.class, ReadAwareSubscriber.class, "subscriber");
        @Nullable @SuppressWarnings("unused") private volatile ReadAwareSubscriber<T> subscriber;

        private volatile BooleanSupplier readInProgressSupplier = () -> false;

        private final Publisher<T> src;
        private final FlushStrategyHolder.FlushSignals signals;

        ReadAwareFlushStrategyHolderImpl(Publisher<T> src, FlushStrategyHolder.FlushSignals signals, int maxPendingWrites) {
            this.src = new Publisher<T>() {
                @Override
                protected void handleSubscribe(Subscriber<? super T> subscriber) {
                    ReadAwareSubscriber<T> rasub = new ReadAwareSubscriber<>(ReadAwareFlushStrategyHolderImpl.this, subscriber, signals, maxPendingWrites);
                    if (subscriberUpdater.compareAndSet(ReadAwareFlushStrategyHolderImpl.this, null, rasub)) {
                        src.doBeforeFinally(() -> ReadAwareFlushStrategyHolderImpl.this.subscriber = null).subscribe(rasub);
                    } else {
                        subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                        subscriber.onError(new IllegalStateException("Only one subscriber allowed for a write source."));
                    }
                }
            };
            this.signals = signals;
        }

        @Override
        public void setReadInProgressSupplier(BooleanSupplier readInProgressSupplier) {
            this.readInProgressSupplier = readInProgressSupplier;
        }

        @Override
        public void readComplete() {
            ReadAwareSubscriber<T> s = subscriber;
            if (s != null) {
                s.sendFlush();
            }
        }

        @Override
        public Publisher<T> getSource() {
            return src;
        }

        @Override
        public FlushStrategyHolder.FlushSignals getFlushSignals() {
            return signals;
        }
    }

    private static final class ReadAwareSubscriber<T> implements Subscriber<T> {

        private static final AtomicIntegerFieldUpdater<ReadAwareSubscriber> unflushedCountUpdater =
                AtomicIntegerFieldUpdater.newUpdater(ReadAwareSubscriber.class, "unflushedCount");
        private final ReadAwareFlushStrategyHolderImpl<T> strategy;
        private final Subscriber<? super T> subscriber;
        private final FlushStrategyHolder.FlushSignals signals;
        private final int maxPendingWrites;
        @SuppressWarnings("unused") private volatile int unflushedCount;

        ReadAwareSubscriber(ReadAwareFlushStrategyHolderImpl<T> strategy, Subscriber<? super T> subscriber,
                            FlushStrategyHolder.FlushSignals signals, int maxPendingWrites) {
            this.strategy = strategy;
            this.subscriber = subscriber;
            this.signals = signals;
            this.maxPendingWrites = maxPendingWrites;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscriber.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            subscriber.onNext(t);
            int unflushed = unflushedCountUpdater.incrementAndGet(this);
            if (shouldFlush(unflushed, maxPendingWrites)) {
                sendFlush();
            }
        }

        @Override
        public void onError(Throwable t) {
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            sendFlush();
            subscriber.onComplete();
        }

        private boolean shouldFlush(int unflushedWrites, int batchSize) {
            return !strategy.readInProgressSupplier.getAsBoolean() || unflushedWrites == batchSize;
        }

        /**
         * Sends a flush signal, using configured {@link FlushStrategyHolder.FlushSignals#signalFlush()}.
         */
        private void sendFlush() {
            int oldUnflushedCount = unflushedCountUpdater.getAndSet(this, 0);
            if (oldUnflushedCount > 0) {
                signals.signalFlush();
            }
        }
    }
}
