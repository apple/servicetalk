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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.OnSubscribeIgnoringSubscriberForOffloading.offloadWithDummyOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater;

final class ScanWithLifetimePublisher<T, R> extends AbstractNoHandleSubscribePublisher<R> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanWithLifetimePublisher.class);

    private final Publisher<T> original;
    private final Supplier<? extends ScanWithLifetimeMapper<? super T, ? extends R>> mapperSupplier;

    ScanWithLifetimePublisher(Publisher<T> original,
                              Supplier<? extends ScanWithLifetimeMapper<? super T, ? extends R>> mapperSupplier,
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

    private static final class ScanWithSubscriber<T, R> implements Subscriber<T> {
        private static final int NOT_ALLOWED = -1;
        private static final int STATE_INIT = 0;
        private static final int STATE_IN_SIGNAL = 1;
        private static final int STATE_INVALID_DEMAND = 2;
        private static final int STATE_TERMINAL_PENDING = 3;
        private static final int STATE_TERMINATED = 4;
        private static final int STATE_TERMINATED_WITH_REQ_HANDOFF = 5;

        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<ScanWithSubscriber> stateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(ScanWithSubscriber.class, "state");

        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<ScanWithSubscriber> demandUpdater =
                newUpdater(ScanWithSubscriber.class, "demand");

        private final Subscriber<? super R> subscriber;
        private final SignalOffloader signalOffloader;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        private final ScanWithLifetimeMapper<? super T, ? extends R> mapper;
        private volatile long demand;
        private volatile int state = STATE_INIT;

        /**
         * Retains the {@link #onError(Throwable)} cause for use in the {@link Subscription}.
         * Happens-before relationship with {@link #demand} means no volatile or other synchronization required.
         */
        @Nullable
        private Throwable errorCause;

        ScanWithSubscriber(final Subscriber<? super R> subscriber,
                           final ScanWithLifetimeMapper<? super T, ? extends R> mapper,
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
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(final long n) {
                    if (!isRequestNValid(n)) {
                        handleInvalidDemand(n);
                    } else if (demandUpdater.getAndAccumulate(ScanWithSubscriber.this, n,
                            FlowControlUtils::addWithOverflowProtectionIfNotNegative) < 0) {

                        for (;;) {
                            int theState = stateUpdater.get(ScanWithSubscriber.this);
                            if (theState >= STATE_TERMINATED) {
                                return;
                            }

                            if (stateUpdater.compareAndSet(ScanWithSubscriber.this, theState,
                                    STATE_TERMINATED_WITH_REQ_HANDOFF)) {
                                try {
                                    // Deliver and finalize only if the state was STATE_TERMINAL_PENDING and we won the
                                    // CAS otherwise the SIGNAL will process both
                                    if (theState == STATE_TERMINAL_PENDING && errorCause != null) {
                                        deliverOnError(errorCause, newOffloadedSubscriber());
                                    } else if (theState == STATE_TERMINAL_PENDING) {
                                        deliverOnComplete(newOffloadedSubscriber());
                                    }
                                } finally {
                                    mapper.onFinalize();
                                }
                            }
                        }
                    } else {
                        subscription.request(n);
                    }
                }

                @Override
                public void cancel() {
                    try {
                        subscription.cancel();
                    } finally {
                        for (;;) {
                            int theState = stateUpdater.get(ScanWithSubscriber.this);
                            if (theState >= STATE_TERMINATED) {
                                break;
                            }

                            if (stateUpdater.compareAndSet(ScanWithSubscriber.this, theState, STATE_TERMINATED)) {
                                // Finalize only if the state was not IN_SIGNAL, otherwise handover finalization
                                // to the signal in-progress
                                if (theState != STATE_IN_SIGNAL) {
                                    mapper.onFinalize();
                                }

                                break;
                            }
                        }
                    }
                }

                private void handleInvalidDemand(final long n) {
                    demandUpdater.getAndSet(ScanWithSubscriber.this, -1);

                    // If there is a terminal pending then the upstream source cannot deliver an error because
                    // duplicate terminal signals are not allowed. otherwise we let upstream deliver the error.
                    final int theState = stateUpdater.getAndSet(ScanWithSubscriber.this, STATE_INVALID_DEMAND);
                    if (theState == STATE_TERMINAL_PENDING) {
                        newOffloadedSubscriber().onError(newExceptionForInvalidRequestN(n));
                    } else {
                        // STATE_INVALID_DEMAND will be allowed to process signals from upstream
                        subscription.request(n);
                    }
                }

                private Subscriber<? super R> newOffloadedSubscriber() {
                    return offloadWithDummyOnSubscribe(subscriber, signalOffloader, contextMap, contextProvider);
                }
            });
        }

        @Override
        public void onNext(@Nullable final T t) {
            int theState = tryAcquireSignal();
            if (theState == NOT_ALLOWED) {
                return;
            }

            try {
                // If anything throws in onNext the source is responsible for catching the error,
                // cancelling the associated subscription, and propagate an onError.
                final R mapped = mapper.mapOnNext(t);
                demandUpdater.decrementAndGet(this);
                subscriber.onNext(mapped);
            } finally {
                releaseAndFinalizeIfNeeded(theState);
            }
        }

        @Override
        public void onError(final Throwable t) {
            boolean doMap = false;
            boolean onErrorPropagated = false;

            int theState = tryAcquireSignal();
            if (theState == NOT_ALLOWED) {
                return;
            }

            // Done after this signal
            theState = STATE_TERMINATED;

            try {
                errorCause = t;
                try {
                    doMap = mapper.mapTerminal();
                } catch (Throwable cause) {
                    subscriber.onError(cause);
                    onErrorPropagated = true;
                    return;
                }

                final long currDemand = demandUpdater.getAndDecrement(this);
                if (doMap) {
                    if (currDemand > 0) {
                        onErrorPropagated = deliverOnError(t, subscriber);
                    } else if (currDemand == 0) {
                        theState = STATE_TERMINAL_PENDING;
                    } else {
                        // Either we previously saw invalid request n, or upstream has sent a duplicate
                        // terminal event. In either circumstance we propagate the error downstream and bail.
                        subscriber.onError(t);
                        onErrorPropagated = true;
                    }
                } else {
                    subscriber.onError(t);
                    onErrorPropagated = true;
                }
            } finally {
                releaseWithTerminalAndFinalizeIfNeeded(theState, doMap, onErrorPropagated, t);
            }
        }

        @Override
        public void onComplete() {
            int theState = tryAcquireSignal();
            if (theState == NOT_ALLOWED) {
                return;
            }

            // Done after this signal
            theState = STATE_TERMINATED;

            boolean doMap = false;
            boolean onErrorPropagated = false;
            try {
                try {
                    doMap = mapper.mapTerminal();
                } catch (Throwable cause) {
                    onErrorPropagated = true;
                    subscriber.onError(cause);
                    return;
                }

                final long currDemand = demandUpdater.getAndDecrement(this);
                if (doMap) {
                    if (currDemand > 0) {
                        onErrorPropagated = deliverOnComplete(subscriber);
                    } else if (currDemand == 0) {
                        theState = STATE_TERMINAL_PENDING;
                    } else {
                        // Either we previously saw invalid request n, or upstream has sent a duplicate terminal event.
                        // In either circumstance we propagate the error downstream and bail.
                        subscriber.onError(new IllegalStateException("onComplete with invalid demand: " + currDemand));
                        onErrorPropagated = true;
                    }
                } else {
                    subscriber.onComplete();
                }
            } finally {
                releaseWithTerminalAndFinalizeIfNeeded(theState, doMap, onErrorPropagated, null);
            }
        }

        private boolean deliverOnError(Throwable t, Subscriber<? super R> subscriber) {
            try {
                subscriber.onNext(mapper.mapOnError(t));
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return true;
            }
            subscriber.onComplete();
            return false;
        }

        private boolean deliverOnComplete(Subscriber<? super R> subscriber) {
            try {
                subscriber.onNext(mapper.mapOnComplete());
            } catch (Throwable cause) {
                subscriber.onError(cause);
                return true;
            }
            subscriber.onComplete();
            return false;
        }

        private int tryAcquireSignal() {
            int theState = stateUpdater.get(ScanWithSubscriber.this);
            if (theState >= STATE_TERMINATED) {
                return NOT_ALLOWED;
            }

            // No need to loop since the signals are guaranteed to be signalled serially,
            // thus a CAS fail means cancellation won, so we can safely ignore it.
            if (stateUpdater.compareAndSet(ScanWithSubscriber.this, theState, STATE_IN_SIGNAL)) {
                return theState;
            }

            return NOT_ALLOWED;
        }

        private void releaseAndFinalizeIfNeeded(final int newState) {
            final boolean finalizationHandedOver = !stateUpdater.compareAndSet(this, STATE_IN_SIGNAL, newState);
            if (finalizationHandedOver || newState == STATE_TERMINATED) {
                // state changed while we were in progress, or new state is TERMINATE
                // finalization is our responsibility
                try {
                    mapper.onFinalize();
                } catch (Throwable cause) {
                    subscriber.onError(cause);
                }
            }
        }

        private void releaseWithTerminalAndFinalizeIfNeeded(final int newState, final boolean doMap,
                                                            boolean onErrorPropagated, @Nullable final Throwable t) {
            final boolean finalizationHandedOver = !stateUpdater.compareAndSet(this, STATE_IN_SIGNAL, newState);
            if (finalizationHandedOver || newState == STATE_TERMINATED) {
                // state changed while we were in progress, or new state is TERMINATE
                // finalization is our responsibility

                try {
                    if (doMap && state == STATE_TERMINATED_WITH_REQ_HANDOFF) {
                        // If a request came while we are in progress, setting the state to
                        // STATE_TERMINATED_REQ_HANDOFF then we can handle the last delivery too.

                        if (t == null) {
                            onErrorPropagated = deliverOnComplete(subscriber);
                        } else {
                            onErrorPropagated = deliverOnError(t, subscriber);
                        }
                    }
                } finally {
                    try {
                        mapper.onFinalize();
                    } catch (Throwable cause) {
                        if (onErrorPropagated) {
                            LOGGER.error("Unexpected error occurred during finalization.", cause);
                        } else {
                            subscriber.onError(cause);
                        }
                    }
                }
            }
        }
    }
}
