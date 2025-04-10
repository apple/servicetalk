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

import io.servicetalk.concurrent.api.ScanWithPublisher.ScanMapperAdapter;
import io.servicetalk.concurrent.api.ScanWithPublisher.ScanWithSubscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class ScanWithLifetimePublisher<T, R> extends AbstractNoHandleSubscribePublisher<R> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanWithLifetimePublisher.class);

    private final Publisher<T> original;
    private final Supplier<? extends ScanLifetimeMapper<? super T, ? extends R>> mapperSupplier;

    ScanWithLifetimePublisher(Publisher<T> original,
                              @SuppressWarnings("deprecation")
                              Supplier<? extends ScanWithLifetimeMapper<? super T, ? extends R>> mapperSupplier) {
        this(new SupplierScanMapperLifetime<>(mapperSupplier), original);
    }

    ScanWithLifetimePublisher(Supplier<? extends ScanLifetimeMapper<? super T, ? extends R>> mapperSupplier,
                              Publisher<T> original) {
        this.mapperSupplier = requireNonNull(mapperSupplier);
        this.original = original;
    }

    @Override
    CapturedContext contextForSubscribe(AsyncContextProvider provider) {
        return provider.captureContext();
    }

    @Override
    void handleSubscribe(final Subscriber<? super R> subscriber,
                         final CapturedContext capturedContext, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ScanWithLifetimeSubscriber<>(subscriber, mapperSupplier.get(),
                capturedContext, contextProvider), capturedContext, contextProvider);
    }

    /**
     * Wraps the {@link ScanWithSubscriber} to provide mutual exclusion to the {@link ScanLifetimeMapper#afterFinally()}
     * call and guarantee a 'no-use-after-free' contract.
     */
    private static final class ScanWithLifetimeSubscriber<T, R> extends ScanWithSubscriber<T, R> {
        private static final int STATE_UNLOCKED = 0;
        private static final int STATE_BUSY = 1;
        private static final int STATE_FINALIZED = 2;
        private static final int STATE_FINALIZE_PENDING_FOR_SUBSCRIBER = 3;

        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<ScanWithLifetimeSubscriber> stateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(ScanWithLifetimeSubscriber.class, "state");

        private volatile int state = STATE_UNLOCKED;

        private final ScanLifetimeMapper<? super T, ? extends R> mapper;

        ScanWithLifetimeSubscriber(final Subscriber<? super R> subscriber,
                                   final ScanLifetimeMapper<? super T, ? extends R> mapper,
                                   final CapturedContext capturedContext, final AsyncContextProvider contextProvider) {
            super(subscriber, mapper, contextProvider, capturedContext);
            this.mapper = requireNonNull(mapper);
        }

        @Override
        protected void onCancel() {
            for (;;) {
                final int prevState = state;
                if (prevState == STATE_BUSY) {
                    if (stateUpdater.compareAndSet(this, STATE_BUSY, STATE_FINALIZE_PENDING_FOR_SUBSCRIBER)) {
                        break;
                    }
                } else if (prevState == STATE_UNLOCKED) {
                    if (stateUpdater.compareAndSet(this, STATE_UNLOCKED, STATE_FINALIZED)) {
                        finalize0();
                        break;
                    }
                } else {
                    assert prevState == STATE_FINALIZED || prevState == STATE_FINALIZE_PENDING_FOR_SUBSCRIBER;
                    break;
                }
            }
        }

        @Override
        public void onNext(@Nullable final T t) {
            boolean reentry = false;
            for (;;) {
                final int prevState = state;
                if (prevState == STATE_BUSY || prevState == STATE_FINALIZE_PENDING_FOR_SUBSCRIBER) {
                    reentry = true;
                    break;
                } else if (prevState == STATE_FINALIZED) {
                    return;
                } else if (stateUpdater.compareAndSet(this, STATE_UNLOCKED, STATE_BUSY)) {
                    break;
                }
            }

            try {
                super.onNext(t);
            } finally {
                // Re-entry -> don't unlock
                if (!reentry) {
                    for (;;) {
                        final int prevState = state;
                        assert prevState != STATE_UNLOCKED && prevState != STATE_FINALIZED;
                        if (prevState == STATE_BUSY) {
                            if (stateUpdater.compareAndSet(this, STATE_BUSY, STATE_UNLOCKED)) {
                                break;
                            }
                        } else if (stateUpdater.compareAndSet(this, STATE_FINALIZE_PENDING_FOR_SUBSCRIBER,
                                STATE_FINALIZED)) {
                            finalize0();
                            break;
                        }
                    }
                }
            }
        }

        @Override
        public void onError(final Throwable t) {
            boolean reentry = false;
            for (;;) {
                final int prevState = state;
                if (prevState == STATE_BUSY || prevState == STATE_FINALIZE_PENDING_FOR_SUBSCRIBER) {
                    reentry = true;
                    break;
                } else if (prevState == STATE_FINALIZED) {
                    return;
                } else if (stateUpdater.compareAndSet(this, STATE_UNLOCKED, STATE_BUSY)) {
                    break;
                }
            }

            boolean completed = true;
            try {
                completed = super.onError0(t);
            } finally {
                releaseFromTerminal(reentry, completed);
            }
        }

        @Override
        public void onComplete() {
            boolean reentry = false;
            for (;;) {
                final int prevState = state;
                if (prevState == STATE_BUSY || prevState == STATE_FINALIZE_PENDING_FOR_SUBSCRIBER) {
                    reentry = true;
                    break;
                } else if (prevState == STATE_FINALIZED) {
                    return;
                } else if (stateUpdater.compareAndSet(this, STATE_UNLOCKED, STATE_BUSY)) {
                    break;
                }
            }

            boolean completed = true;
            try {
                completed = super.onComplete0();
            } finally {
                releaseFromTerminal(reentry, completed);
            }
        }

        @Override
        protected void deliverAllTerminalFromSubscription(
                final ScanMapper.MappedTerminal<? extends R> mappedTerminal,
                final Subscriber<? super R> subscriber) {
            if (shouldDeliverFromSubscription()) {
                try {
                    super.deliverAllTerminalFromSubscription(mappedTerminal, subscriber);
                } finally {
                    // Done, transit to FINALIZED.
                    // No need to CAS, we have exclusion, and any cancellations will hand-over finalization to us.
                    state = STATE_FINALIZED;
                    finalize0();
                }
            }
        }

        private boolean shouldDeliverFromSubscription() {
            // At this point the Subscriber has already delivered a terminal event, and there is no concurrency allowed
            // on the Subscription thread, so we don't need to account for concurrency here. We also don't need to
            // change the state value because we always transition to STATE_FINALIZED after this point.
            return state != STATE_FINALIZED;
        }

        private void releaseFromTerminal(final boolean reentry, final boolean completed) {
            if (!completed) {
                // Demand wasn't sufficient to deliver. If reentry re-evaluate when stack unwinds. If not reentry we
                // either unlock here or finalize if subscription was cancelled concurrently.
                if (!reentry) {
                    for (;;) {
                        final int prevState = state;
                        assert prevState != STATE_UNLOCKED && prevState != STATE_FINALIZED;
                        if (prevState == STATE_BUSY) {
                            if (stateUpdater.compareAndSet(this, STATE_BUSY, STATE_UNLOCKED)) {
                                break;
                            }
                        } else if (stateUpdater.compareAndSet(this, STATE_FINALIZE_PENDING_FOR_SUBSCRIBER,
                                STATE_FINALIZED)) {
                            finalize0();
                            break;
                        }
                    }
                }
            } else if (reentry) {
                // No need to CAS, reentry root (ie. onNext) will do that.
                state = STATE_FINALIZE_PENDING_FOR_SUBSCRIBER;
            } else {
                // No need to CAS here, we already have exclusion, and any cancellations will hand-over
                // finalization to us anyhow.
                state = STATE_FINALIZED;
                finalize0();
            }
        }

        private void finalize0() {
            try {
                mapper.afterFinally();
            } catch (Throwable cause) {
                LOGGER.error("Unexpected error occurred during finalization.", cause);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static final class SupplierScanMapperLifetime<T, R> implements
                                                                       Supplier<ScanLifetimeMapper<T, R>> {
        private final Supplier<? extends ScanWithLifetimeMapper<? super T, ? extends R>> mapperSupplier;

        private SupplierScanMapperLifetime(
                final Supplier<? extends ScanWithLifetimeMapper<? super T, ? extends R>> mapperSupplier) {
            this.mapperSupplier = requireNonNull(mapperSupplier);
        }

        @Override
        public ScanLifetimeMapper<T, R> get() {
            return new ScanMapperLifetimeAdapter<>(mapperSupplier.get());
        }
    }

    @SuppressWarnings("deprecation")
    private static final class ScanMapperLifetimeAdapter<T, R>
            extends ScanMapperAdapter<T, R, ScanWithLifetimeMapper<? super T, ? extends R>>
            implements ScanLifetimeMapper<T, R> {
        ScanMapperLifetimeAdapter(final ScanWithLifetimeMapper<? super T, ? extends R> mapper) {
            super(mapper);
        }

        @Override
        public void afterFinally() {
            mapper.afterFinally();
        }
    }
}
