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
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.OnSubscribeIgnoringSubscriberForOffloading.offloadWithDummyOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

final class ScanWithPublisher<T, R> extends AbstractNoHandleSubscribePublisher<R> {
    private final Publisher<T> original;
    private final Supplier<? extends ScanWithMapper<? super T, ? extends R>> mapperSupplier;

    ScanWithPublisher(Publisher<T> original, Supplier<R> initial, BiFunction<R, ? super T, R> accumulator,
                      Executor executor) {
        this(original, new SupplierScanWithMapper<>(initial, accumulator), executor);
    }

    ScanWithPublisher(Publisher<T> original, Supplier<? extends ScanWithMapper<? super T, ? extends R>> mapperSupplier,
                      Executor executor) {
        super(executor, true);
        this.mapperSupplier = requireNonNull(mapperSupplier);
        this.original = original;
    }

    @Override
    void handleSubscribe(final Subscriber<? super R> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ScanWithSubscriber<>(subscriber, mapperSupplier.get(), signalOffloader,
                contextMap, contextProvider), signalOffloader, contextMap, contextProvider);
    }

    static class ScanWithSubscriber<T, R> implements Subscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<ScanWithSubscriber> demandUpdater =
                newUpdater(ScanWithSubscriber.class, "demand");

        private static final long TERMINATED = Long.MIN_VALUE;
        private static final long TERMINAL_PENDING = TERMINATED + 1;
        /**
         * We don't want to invoke {@link ScanWithMapper#mapOnError(Throwable)} for invalid demand because we may never
         * get enough demand to deliver an {@link #onNext(Object)} to the downstream subscriber. {@code -1} to avoid
         * {@link #demand} underflow in onNext (in case the source doesn't deliver a timely error).
         */
        private static final long INVALID_DEMAND = -1;

        private final Subscriber<? super R> subscriber;
        private final SignalOffloader signalOffloader;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        private final ScanWithMapper<? super T, ? extends R> mapper;
        private volatile long demand;
        /**
         * Retains the {@link #onError(Throwable)} cause for use in the {@link Subscription}.
         * Happens-before relationship with {@link #demand} means no volatile or other synchronization required.
         */
        @Nullable
        private Throwable errorCause;

        ScanWithSubscriber(final Subscriber<? super R> subscriber, final ScanWithMapper<? super T, ? extends R> mapper,
                           final SignalOffloader signalOffloader, final AsyncContextMap contextMap,
                           final AsyncContextProvider contextProvider) {
            this.subscriber = subscriber;
            this.signalOffloader = signalOffloader;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
            this.mapper = requireNonNull(mapper);
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            subscriber.onSubscribe(newSubscription(subscription));
        }

        private Subscription newSubscription(final Subscription subscription) {
            return new Subscription() {
                @Override
                public void request(final long n) {
                    if (!isRequestNValid(n)) {
                        handleInvalidDemand(n);
                    } else if (demandUpdater.getAndAccumulate(ScanWithSubscriber.this, n,
                            FlowControlUtils::addWithOverflowProtectionIfNotNegative) == TERMINAL_PENDING) {
                        demand = TERMINATED;
                        if (errorCause != null) {
                            deliverOnErrorFromSubscription(errorCause, newOffloadedSubscriber());
                        } else {
                            deliverOnCompleteFromSubscription(newOffloadedSubscriber());
                        }
                    } else {
                        subscription.request(n);
                    }
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                    onCancel();
                }

                private void handleInvalidDemand(final long n) {
                    // If there is a terminal pending then the upstream source cannot deliver an error because
                    // duplicate terminal signals are not allowed. otherwise we let upstream deliver the error.
                    if (demandUpdater.getAndSet(ScanWithSubscriber.this, INVALID_DEMAND) == TERMINAL_PENDING) {
                        demand = TERMINATED;
                        newOffloadedSubscriber().onError(newExceptionForInvalidRequestN(n));
                    } else {
                        subscription.request(n);
                    }
                }

                private Subscriber<? super R> newOffloadedSubscriber() {
                    return offloadWithDummyOnSubscribe(subscriber, signalOffloader, contextMap, contextProvider);
                }
            };
        }

        @Override
        public void onNext(@Nullable final T t) {
            // If anything throws in onNext the source is responsible for catching the error, cancelling the associated
            // Subscription, and propagate an onError.
            final R mapped = mapper.mapOnNext(t);
            demandUpdater.decrementAndGet(this);
            subscriber.onNext(mapped);
        }

        @Override
        public void onError(final Throwable t) {
            onError0(t);
        }

        @Override
        public void onComplete() {
            onComplete0();
        }

        /**
         * Return true if onError0 had sufficient demand to deliver.
         */
        protected boolean onError0(final Throwable t) {
            errorCause = t;
            final boolean doMap;
            try {
                doMap = mapper.mapTerminal();
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return true;
            }
            if (doMap) {
                for (;;) {
                    final long currDemand = demand;
                    if (currDemand > 0 && demandUpdater.compareAndSet(this, currDemand, TERMINATED)) {
                        deliverOnError(t, subscriber);
                        break;
                    } else if (currDemand == 0 && demandUpdater.compareAndSet(this, currDemand, TERMINAL_PENDING)) {
                        return false;
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

            return true;
        }

        /**
         * Return true if onComplete0 had sufficient demand to deliver.
         */
        protected boolean onComplete0() {
            final boolean doMap;
            try {
                doMap = mapper.mapTerminal();
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return true;
            }
            if (doMap) {
                for (;;) {
                    final long currDemand = demand;
                    if (currDemand > 0 && demandUpdater.compareAndSet(this, currDemand, TERMINATED)) {
                        deliverOnComplete(subscriber);
                        break;
                    } else if (currDemand == 0 && demandUpdater.compareAndSet(this, currDemand, TERMINAL_PENDING)) {
                        return false;
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

            return true;
        }

        protected void onCancel() {
            //NOOP
        }

        protected void deliverOnErrorFromSubscription(Throwable t, Subscriber<? super R> subscriber) {
            deliverOnError(t, subscriber);
        }

        protected void deliverOnCompleteFromSubscription(Subscriber<? super R> subscriber) {
            deliverOnComplete(subscriber);
        }

        private void deliverOnError(Throwable t, Subscriber<? super R> subscriber) {
            try {
                subscriber.onNext(mapper.mapOnError(t));
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onComplete();
        }

        private void deliverOnComplete(Subscriber<? super R> subscriber) {
            try {
                subscriber.onNext(mapper.mapOnComplete());
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            subscriber.onComplete();
        }
    }

    private static final class SupplierScanWithMapper<T, R> implements Supplier<ScanWithMapper<T, R>> {
        private final BiFunction<R, ? super T, R> accumulator;
        private final Supplier<R> initial;

        SupplierScanWithMapper(Supplier<R> initial, BiFunction<R, ? super T, R> accumulator) {
            this.initial = requireNonNull(initial);
            this.accumulator = requireNonNull(accumulator);
        }

        @Override
        public ScanWithMapper<T, R> get() {
            return new ScanWithMapper<T, R>() {
                @Nullable
                private R state = initial.get();

                @Override
                public R mapOnNext(@Nullable final T next) {
                    state = accumulator.apply(state, next);
                    return state;
                }

                @Override
                public R mapOnError(final Throwable cause) {
                    throw newMapTerminalUnsupported();
                }

                @Override
                public R mapOnComplete() {
                    throw newMapTerminalUnsupported();
                }

                @Override
                public boolean mapTerminal() {
                    return false;
                }
            };
        }

        private static IllegalStateException newMapTerminalUnsupported() {
            throw new IllegalStateException("mapTerminal returns false, this method should never be invoked!");
        }
    }
}
