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

import io.servicetalk.concurrent.api.ScanMapper.MappedTerminal;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.context.api.ContextMap;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.OnSubscribeIgnoringSubscriberForOffloading.wrapWithDummyOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

final class ScanWithPublisher<T, R> extends AbstractNoHandleSubscribePublisher<R> {
    private final Publisher<T> original;
    private final Supplier<? extends ScanMapper<? super T, ? extends R>> mapperSupplier;

    ScanWithPublisher(Publisher<T> original, Supplier<R> initial, BiFunction<R, ? super T, R> accumulator) {
        this(new SupplierScanWithMapper<>(initial, accumulator), original);
    }

    ScanWithPublisher(Publisher<T> original,
                      @SuppressWarnings("deprecation")
                      Supplier<? extends ScanWithMapper<? super T, ? extends R>> mapperSupplier) {
        this(new SupplierScanMapper<>(mapperSupplier), original);
    }

    ScanWithPublisher(Supplier<? extends ScanMapper<? super T, ? extends R>> mapperSupplier,
                      Publisher<T> original) {
        this.mapperSupplier = requireNonNull(mapperSupplier);
        this.original = original;
    }

    @Override
    ContextMap contextForSubscribe(AsyncContextProvider provider) {
        return provider.saveContext();
    }

    @Override
    void handleSubscribe(final Subscriber<? super R> subscriber,
                         final ContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ScanWithSubscriber<>(subscriber, mapperSupplier.get(),
                contextProvider, contextMap), contextMap, contextProvider);
    }

    static class ScanWithSubscriber<T, R> implements Subscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<ScanWithSubscriber> demandUpdater =
                newUpdater(ScanWithSubscriber.class, "demand");

        private static final long TERMINATED = Long.MIN_VALUE;
        private static final long TERMINAL_PENDING = TERMINATED + 1;
        /**
         * We don't want to invoke {@link ScanMapper#mapOnError(Throwable)} for invalid demand because we may never
         * get enough demand to deliver an {@link #onNext(Object)} to the downstream subscriber. {@code -1} to avoid
         * {@link #demand} underflow in onNext (in case the source doesn't deliver a timely error).
         */
        private static final long INVALID_DEMAND = -1;

        private final Subscriber<? super R> subscriber;
        private final ContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        private final ScanMapper<? super T, ? extends R> mapper;
        private volatile long demand;
        /**
         * Retains the {@link MappedTerminal} cause for use in the {@link Subscription}.
         * Happens-before relationship with {@link #demand} means no volatile or other synchronization required.
         */
        @Nullable
        private MappedTerminal<? extends R> mappedTerminal;

        ScanWithSubscriber(final Subscriber<? super R> subscriber,
                           final ScanMapper<? super T, ? extends R> mapper,
                           final AsyncContextProvider contextProvider, final ContextMap contextMap) {
            this.subscriber = subscriber;
            this.contextProvider = contextProvider;
            this.contextMap = contextMap;
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
                        assert mappedTerminal != null;
                        deliverAllTerminalFromSubscription(mappedTerminal, newOffloadedSubscriber());
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
                    return wrapWithDummyOnSubscribe(subscriber, contextMap, contextProvider);
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
         * Executes the on-error signal and returns {@code true} if demand was sufficient to deliver the result of the
         * mapped {@code Throwable} and terminal signal.
         *
         * @param t The throwable to propagate
         * @return {@code true} if the demand was sufficient to deliver the result of the mapped {@code Throwable} with
         * terminal signal.
         */
        protected boolean onError0(final Throwable t) {
            try {
                mappedTerminal = mapper.mapOnError(t);
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return true;
            }

            if (mappedTerminal != null) {
                return deliverAllTerminal(mappedTerminal, subscriber, t);
            }
            demand = TERMINATED;
            subscriber.onError(t);
            return true;
        }

        /**
         * Executes the on-completed signal and returns {@code true} if demand was sufficient to deliver the concat item
         * from {@link ScanMapper#mapOnComplete()} downstream.
         *
         * @return {@code true} if demand was sufficient to deliver the concat item from
         * {@link ScanMapper#mapOnComplete()} downstream.
         */
        protected boolean onComplete0() {
            try {
                mappedTerminal = mapper.mapOnComplete();
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return true;
            }
            if (mappedTerminal != null) {
                return deliverAllTerminal(mappedTerminal, subscriber, null);
            }
            demand = TERMINATED;
            subscriber.onComplete();
            return true;
        }

        protected void onCancel() {
            //NOOP
        }

        protected void deliverAllTerminalFromSubscription(final MappedTerminal<? extends R> mappedTerminal,
                                                          final Subscriber<? super R> subscriber) {
            deliverOnNextAndTerminal(mappedTerminal, subscriber);
        }

        private boolean deliverAllTerminal(final MappedTerminal<? extends R> mappedTerminal,
                                           final Subscriber<? super R> subscriber,
                                           @Nullable final Throwable originalCause) {
            final boolean onNextValid;
            try {
                onNextValid = mappedTerminal.onNextValid();
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return true;
            }
            if (onNextValid) {
                for (;;) {
                    final long currDemand = demand;
                    if (currDemand > 0 && demandUpdater.compareAndSet(this, currDemand, TERMINATED)) {
                        deliverOnNextAndTerminal(mappedTerminal, subscriber);
                        break;
                    } else if (currDemand == 0 && demandUpdater.compareAndSet(this, currDemand, TERMINAL_PENDING)) {
                        return false;
                    } else if (currDemand < 0) {
                        // Either we previously saw invalid request n, or upstream has sent a duplicate terminal
                        // event. In either circumstance we propagate the error downstream and bail.
                        subscriber.onError(originalCause != null ? originalCause :
                                new IllegalStateException("onComplete with invalid demand: " + currDemand));
                        break;
                    }
                }
            } else {
                demand = TERMINATED;
                deliverTerminal(mappedTerminal, subscriber);
            }
            return true;
        }

        private void deliverTerminal(final MappedTerminal<? extends R> mappedTerminal,
                                     final Subscriber<? super R> subscriber) {
            final Throwable cause;
            try {
                cause = mappedTerminal.terminal();
            } catch (Throwable cause2) {
                subscriber.onError(cause2);
                return;
            }
            if (cause == null) {
                subscriber.onComplete();
            } else {
                subscriber.onError(cause);
            }
        }

        private void deliverOnNextAndTerminal(final MappedTerminal<? extends R> mappedTerminal,
                                              final Subscriber<? super R> subscriber) {
            try {
                assert mappedTerminal.onNextValid();
                subscriber.onNext(mappedTerminal.onNext());
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return;
            }
            deliverTerminal(mappedTerminal, subscriber);
        }
    }

    @SuppressWarnings("deprecation")
    private static final class SupplierScanMapper<T, R> implements Supplier<ScanMapper<T, R>> {
        private final Supplier<? extends ScanWithMapper<? super T, ? extends R>> mapperSupplier;

        SupplierScanMapper(Supplier<? extends ScanWithMapper<? super T, ? extends R>> mapperSupplier) {
            this.mapperSupplier = requireNonNull(mapperSupplier);
        }

        @Override
        public ScanMapper<T, R> get() {
            return new ScanMapperAdapter<>(mapperSupplier.get());
        }
    }

    private static final class SupplierScanWithMapper<T, R> implements Supplier<ScanMapper<T, R>> {
        private final BiFunction<R, ? super T, R> accumulator;
        private final Supplier<R> initial;

        SupplierScanWithMapper(Supplier<R> initial, BiFunction<R, ? super T, R> accumulator) {
            this.initial = requireNonNull(initial);
            this.accumulator = requireNonNull(accumulator);
        }

        @Override
        public ScanMapper<T, R> get() {
            return new ScanMapper<T, R>() {
                @Nullable
                private R state = initial.get();

                @Override
                public R mapOnNext(@Nullable final T next) {
                    state = accumulator.apply(state, next);
                    return state;
                }

                @Nullable
                @Override
                public MappedTerminal<R> mapOnError(final Throwable cause) {
                    return null;
                }

                @Nullable
                @Override
                public MappedTerminal<R> mapOnComplete() {
                    return null;
                }
            };
        }
    }

    @SuppressWarnings("deprecation")
    static class ScanMapperAdapter<T, R, X extends ScanWithMapper<? super T, ? extends R>>
            implements ScanMapper<T, R> {
        final X mapper;

        ScanMapperAdapter(final X mapper) {
            this.mapper = requireNonNull(mapper);
        }

        @Nullable
        @Override
        public R mapOnNext(@Nullable final T next) {
            return mapper.mapOnNext(next);
        }

        @Nullable
        @Override
        public MappedTerminal<R> mapOnError(final Throwable cause) throws Throwable {
            return mapper.mapTerminal() ? new FixedMappedTerminal<>(mapper.mapOnError(cause)) : null;
        }

        @Nullable
        @Override
        public MappedTerminal<R> mapOnComplete() {
            return mapper.mapTerminal() ? new FixedMappedTerminal<>(mapper.mapOnComplete()) : null;
        }
    }

    private static final class FixedMappedTerminal<R> implements MappedTerminal<R> {
        @Nullable
        private final R onNext;

        private FixedMappedTerminal(@Nullable final R onNext) {
            this.onNext = onNext;
        }

        @Nullable
        @Override
        public R onNext() {
            return onNext;
        }

        @Override
        public boolean onNextValid() {
            return true;
        }

        @Nullable
        @Override
        public Throwable terminal() {
            return null;
        }
    }
}
