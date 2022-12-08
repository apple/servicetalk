/*
 * Copyright © 2018, 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.client.api.RequestConcurrencyController;
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.internal.LatestValueSubscriber;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.client.api.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.client.api.RequestConcurrencyController.Result.RejectedPermanently;
import static io.servicetalk.client.api.RequestConcurrencyController.Result.RejectedTemporary;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Factory for common {@link ReservableRequestConcurrencyController}s.
 */
final class ReservableRequestConcurrencyControllers {

    private ReservableRequestConcurrencyControllers() {
        // no instances
    }

    /**
     * Create a new instance of {@link ReservableRequestConcurrencyController}.
     *
     * @param maxConcurrency A {@link Publisher} that provides the maximum allowed concurrency updates.
     * @param onClosing A {@link Completable} that when terminated no more calls to
     * {@link RequestConcurrencyController#tryRequest()} are expected to succeed.
     * @param initialMaxConcurrency The initial maximum value for concurrency, until {@code maxConcurrencySetting}
     * provides data.
     * @return a new instance of {@link ReservableRequestConcurrencyController}.
     */
    static ReservableRequestConcurrencyController newController(
            final Publisher<? extends ConsumableEvent<Integer>> maxConcurrency, final Completable onClosing,
            final int initialMaxConcurrency) {
        return new ReservableRequestConcurrencyControllerMulti(maxConcurrency, onClosing, initialMaxConcurrency);
    }

    /**
     * A {@link ConsumableEvent} which ignores {@link #eventConsumed()}.
     *
     * @param <T> The type of event.
     */
    static final class IgnoreConsumedEvent<T> implements ConsumableEvent<T> {
        private final T event;

        /**
         * Create a new instance.
         *
         * @param event The event to return from {@link #event()}.
         */
        IgnoreConsumedEvent(final T event) {
            this.event = requireNonNull(event);
        }

        @Override
        public T event() {
            return event;
        }

        @Override
        public void eventConsumed() {
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{event=" + event + '}';
        }
    }

    private abstract static class AbstractReservableRequestConcurrencyController
            implements ReservableRequestConcurrencyController {
        private static final AtomicIntegerFieldUpdater<AbstractReservableRequestConcurrencyController>
                pendingRequestsUpdater = newUpdater(AbstractReservableRequestConcurrencyController.class,
                "pendingRequests");

        private static final int STATE_QUIT = -2;
        private static final int STATE_RESERVED = -1;
        private static final int STATE_IDLE = 0;

        /*
         * Following semantics:
         * STATE_RESERVED if this is reserved.
         * STATE_QUIT if quit command issued.
         * STATE_IDLE if connection is not used.
         * pending request count if none of the above states.
         */
        @SuppressWarnings("unused")
        private volatile int pendingRequests;
        private final LatestValueSubscriber<Integer> maxConcurrencyHolder;

        AbstractReservableRequestConcurrencyController(
                final Publisher<? extends ConsumableEvent<Integer>> maxConcurrency,
                final Completable onClosing) {
            maxConcurrencyHolder = new LatestValueSubscriber<>();
            // Subscribe to onClosing() before maxConcurrency, this order increases the chances of capturing the
            // STATE_QUIT before observing 0 from maxConcurrency which could lead to more ambiguous max concurrency
            // error messages for the users on connection tear-down.
            toSource(onClosing).subscribe(new CompletableSource.Subscriber() {
                @Override
                public void onSubscribe(Cancellable cancellable) {
                    // No op
                }

                @Override
                public void onComplete() {
                    assert pendingRequests != STATE_QUIT;
                    pendingRequests = STATE_QUIT;
                }

                @Override
                public void onError(Throwable ignored) {
                    assert pendingRequests != STATE_QUIT;
                    pendingRequests = STATE_QUIT;
                }
            });
            toSource(maxConcurrency
                    .afterOnNext(ConsumableEvent::eventConsumed)
                    .map(ConsumableEvent::event)
            ).subscribe(maxConcurrencyHolder);
        }

        @Override
        public final void requestFinished() {
            pendingRequestsUpdater.decrementAndGet(this);
        }

        @Override
        public boolean tryReserve() {
            return pendingRequestsUpdater.compareAndSet(this, STATE_IDLE, STATE_RESERVED);
        }

        @Override
        public Completable releaseAsync() {
            return new SubscribableCompletable() {
                @Override
                protected void handleSubscribe(Subscriber subscriber) {
                    try {
                        subscriber.onSubscribe(IGNORE_CANCEL);
                    } catch (Throwable cause) {
                        handleExceptionFromOnSubscribe(subscriber, cause);
                        return;
                    }
                    // Ownership is maintained by the caller.
                    if (pendingRequestsUpdater.compareAndSet(AbstractReservableRequestConcurrencyController.this,
                            STATE_RESERVED, STATE_IDLE)) {
                        subscriber.onComplete();
                    } else {
                        subscriber.onError(new IllegalStateException("Resource " + this +
                                (pendingRequests == STATE_QUIT ? " is closed." : " was not reserved.")));
                    }
                }
            };
        }

        final int lastSeenMaxValue(int defaultValue) {
            return maxConcurrencyHolder.lastSeenValue(defaultValue);
        }

        final int pendingRequests() {
            return pendingRequests;
        }

        final boolean casPendingRequests(int oldValue, int newValue) {
            return pendingRequestsUpdater.compareAndSet(this, oldValue, newValue);
        }

        @Override
        public String toString() {
            return "pendingRequests=" + pendingRequests + " maxRequests=" + maxConcurrencyHolder.lastSeenValue(-1);
        }
    }

    private static final class ReservableRequestConcurrencyControllerMulti
            extends AbstractReservableRequestConcurrencyController {
        private final int maxRequests;

        ReservableRequestConcurrencyControllerMulti(final Publisher<? extends ConsumableEvent<Integer>> maxConcurrency,
                                                    final Completable onClosing,
                                                    int maxRequests) {
            super(maxConcurrency, onClosing);
            this.maxRequests = maxRequests;
        }

        @Override
        public Result tryRequest() {
            final int maxConcurrency = lastSeenMaxValue(maxRequests);
            for (;;) {
                final int currentPending = pendingRequests();
                if (currentPending < 0) {
                    return RejectedPermanently;
                }
                if (currentPending >= maxConcurrency) {
                    return RejectedTemporary;
                }
                if (casPendingRequests(currentPending, currentPending + 1)) {
                    return Accepted;
                }
            }
        }
    }
}
