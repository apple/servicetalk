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

import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

final class ScanWithPublisher<T, R> extends AbstractNoHandleSubscribePublisher<R> {
    private final Publisher<T> original;
    private final Supplier<R> initial;
    private final BiFunction<R, ? super T, R> mapper;
    private final Predicate<R> mapTerminal;
    private final BiFunction<R, Throwable, R> onErrorMapper;
    private final UnaryOperator<R> onCompleteMapper;

    ScanWithPublisher(Publisher<T> original, Supplier<R> initial, BiFunction<R, ? super T, R> mapper,
                      Predicate<R> mapTerminal, BiFunction<R, Throwable, R> onErrorMapper,
                      UnaryOperator<R> onCompleteMapper, Executor executor) {
        super(executor, true);
        this.initial = requireNonNull(initial);
        this.mapper = requireNonNull(mapper);
        this.mapTerminal = requireNonNull(mapTerminal);
        this.onErrorMapper = requireNonNull(onErrorMapper);
        this.onCompleteMapper = requireNonNull(onCompleteMapper);
        this.original = original;
    }

    @Override
    void handleSubscribe(final Subscriber<? super R> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.subscribeInternal(new ScanWithSubscriber<>(subscriber, signalOffloader.offloadSubscriber(
                contextProvider.wrapPublisherSubscriber(subscriber, contextMap)), this));
    }

    private static final class ScanWithSubscriber<T, R> implements Subscriber<T> {
        private static final long TERMINATED = Long.MIN_VALUE;
        private static final long TERMINAL_PENDING = TERMINATED + 1;
        /**
         * We don't want to invoke {@link #onErrorMapper} for invalid demand because we may never get enough demand to
         * deliver an {@link #onNext(Object)} to the downstream subscriber. {@code -1} to avoid {@link #demand}
         * underflow in onNext (in case the source doesn't deliver a timely error).
         */
        private static final long INVALID_DEMAND = -1;
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<ScanWithSubscriber> demandUpdater =
                newUpdater(ScanWithSubscriber.class, "demand");
        private final Subscriber<? super R> subscriber;
        private final Subscriber<? super R> offloadedSubscriber;
        private final ScanWithPublisher<T, R> parent;
        private volatile long demand;
        @Nullable
        private R state;
        /**
         * Retains the {@link #onError(Throwable)} cause for use in the {@link Subscription}.
         * Happens-before relationship with {@link #demand} means no volatile or other synchronization required.
         */
        @Nullable
        private Throwable errorCause;

        ScanWithSubscriber(final Subscriber<? super R> subscriber, final Subscriber<? super R> offloadedSubscriber,
                           final ScanWithPublisher<T, R> parent) {
            this.subscriber = subscriber;
            this.offloadedSubscriber = offloadedSubscriber;
            this.parent = parent;
            this.state = parent.initial.get();
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                    if (!isRequestNValid(n)) {
                        handleInvalidDemand(n);
                    } else if (demandUpdater.getAndAccumulate(ScanWithSubscriber.this, n,
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
                    demand = TERMINATED;
                    subscription.cancel();
                }

                private void handleInvalidDemand(final long n) {
                    // If there is a terminal pending then the upstream source cannot deliver an error because
                    // duplicate terminal signals are not allowed. otherwise we let upstream deliver the error.
                    if (demandUpdater.getAndSet(ScanWithSubscriber.this, INVALID_DEMAND) == TERMINAL_PENDING) {
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
            // If anything throws in onNext the source is responsible for catching the error, cancelling the associated
            // Subscription, and propagate an onError.
            state = parent.mapper.apply(state, t);
            demandUpdater.decrementAndGet(this);
            subscriber.onNext(state);
        }

        @Override
        public void onError(final Throwable t) {
            errorCause = t;
            final boolean doMap;
            try {
                doMap = parent.mapTerminal.test(state);
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
                        // Either we previously saw invalid request n, or upstream has sent a duplicate terminal event.
                        // In either circumstance we propagate the error downstream and bail.
                        subscriber.onError(t);
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
                doMap = parent.mapTerminal.test(state);
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
                    } else if (currDemand < 0) {
                        // Either we previously saw invalid request n, or upstream has sent a duplicate terminal event.
                        // In either circumstance we propagate the error downstream and bail.
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
                subscriber.onNext(parent.onErrorMapper.apply(state, t));
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onComplete();
        }

        private void deliverOnComplete(Subscriber<? super R> subscriber) {
            try {
                subscriber.onNext(parent.onCompleteMapper.apply(state));
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onComplete();
        }
    }
}
