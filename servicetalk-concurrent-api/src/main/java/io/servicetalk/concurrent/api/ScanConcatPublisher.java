/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

final class ScanConcatPublisher<T, R> extends AbstractNoHandleSubscribePublisher<R> {
    private final Publisher<T> original;
    private final Supplier<? extends ScanConcatMapper<? super T, ? extends R>> mapperSupplier;

    ScanConcatPublisher(Publisher<T> original,
                        Supplier<? extends ScanConcatMapper<? super T, ? extends R>> mapperSupplier,
                        Executor executor) {
        super(executor, true);
        this.mapperSupplier = requireNonNull(mapperSupplier);
        this.original = original;
    }

    @Override
    void handleSubscribe(final Subscriber<? super R> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.subscribeInternal(new ScanConcatSubscriber<>(subscriber, signalOffloader.offloadSubscriber(
                contextProvider.wrapPublisherSubscriber(subscriber, contextMap)), mapperSupplier.get()));
    }

    private static final class ScanConcatSubscriber<T, R> implements Subscriber<T> {
        private static final long TERMINATED = Long.MIN_VALUE;
        private static final long TERMINAL_PENDING = TERMINATED + 1;
        /**
         * We don't want to invoke {@link ScanConcatMapper#onError(Throwable)} for invalid demand because we may never
         * get enough demand to deliver an {@link #onNext(Object)} to the downstream subscriber. {@code -1} to avoid
         * {@link #demand} underflow in onNext (in case the source doesn't deliver a timely error).
         */
        private static final long INVALID_DEMAND = -1;
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<ScanConcatSubscriber> demandUpdater =
                newUpdater(ScanConcatSubscriber.class, "demand");
        private final Subscriber<? super R> subscriber;
        private final Subscriber<? super R> offloadedSubscriber;
        private final ScanConcatMapper<T, R> mapper;
        private volatile long demand;
        @Nullable
        private Throwable errorCause;

        ScanConcatSubscriber(final Subscriber<? super R> subscriber, final Subscriber<? super R> offloadedSubscriber,
                             final ScanConcatMapper<T, R> mapper) {
            this.subscriber = subscriber;
            this.offloadedSubscriber = offloadedSubscriber;
            this.mapper = requireNonNull(mapper);
        }

        @Override
        public void onSubscribe(final PublisherSource.Subscription subscription) {
            subscriber.onSubscribe(new PublisherSource.Subscription() {
                @Override
                public void request(final long n) {
                    if (!isRequestNValid(n)) {
                        handleInvalidDemand(n);
                    } else if (demandUpdater.getAndAccumulate(ScanConcatSubscriber.this, n,
                            FlowControlUtils::addWithOverflowProtectionIfNotNegative) == TERMINAL_PENDING) {
                        demand = TERMINATED;
                        if (errorCause != null) {
                            deliverOnError(errorCause, offloadedSubscriber);
                        } else {
                            deliverOnComplete(offloadedSubscriber);
                        }
                    } else {
                        subscription.request(n);
                    }
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                }

                private void handleInvalidDemand(final long n) {
                    // If there is a terminal pending then the upstream source cannot deliver an error because
                    // duplicate terminal signals are not allowed. otherwise we let upstream deliver the error.
                    if (demandUpdater.getAndSet(ScanConcatSubscriber.this, INVALID_DEMAND) == TERMINAL_PENDING) {
                        demand = TERMINATED;
                        offloadedSubscriber.onError(newExceptionForInvalidRequestN(n));
                    } else {
                        subscription.request(n);
                    }
                }
            });
        }

        @Override
        public void onNext(@Nullable final T t) {
            demandUpdater.decrementAndGet(this);
            subscriber.onNext(mapper.onNext(t));
        }

        @Override
        public void onError(final Throwable t) {
            errorCause = t;
            final boolean doMap;
            try {
                doMap = mapper.mapTerminalSignal();
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            if (doMap) {
                for (;;) {
                    final long currDemand = demand;
                    if (currDemand > 0 && demandUpdater.compareAndSet(this, currDemand, TERMINATED)) {
                        deliverOnError(t, subscriber);
                        break;
                    } else if (currDemand == 0 && demandUpdater.compareAndSet(this, currDemand, TERMINAL_PENDING)) {
                        break;
                    } else if (currDemand < 0) {
                        subscriber.onError(t); // either invalid request n, or we have already been terminated.
                        break;
                    }
                }
            } else {
                demand = TERMINATED;
                subscriber.onError(t);
            }
        }

        @Override
        public void onComplete() {
            final boolean doMap;
            try {
                doMap = mapper.mapTerminalSignal();
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            if (doMap) {
                for (;;) {
                    final long currDemand = demand;
                    if (currDemand > 0 && demandUpdater.compareAndSet(this, currDemand, TERMINATED)) {
                        deliverOnComplete(subscriber);
                        break;
                    } else if (currDemand == 0 && demandUpdater.compareAndSet(this, currDemand, TERMINAL_PENDING)) {
                        break;
                    } else if (currDemand < 0) { // either invalid request n, or we have already been terminated.
                        subscriber.onError(new IllegalStateException("onComplete with invalid demand: " + currDemand));
                        break;
                    }
                }
            } else {
                demand = TERMINATED;
                subscriber.onComplete();
            }
        }

        private void deliverOnError(Throwable t, Subscriber<? super R> subscriber) {
            try {
                subscriber.onNext(mapper.onError(t));
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onComplete();
        }

        private void deliverOnComplete(Subscriber<? super R> subscriber) {
            try {
                subscriber.onNext(mapper.onComplete());
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onComplete();
        }
    }
}
