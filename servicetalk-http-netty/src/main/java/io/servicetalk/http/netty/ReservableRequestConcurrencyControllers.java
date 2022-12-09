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
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.RequestConcurrencyController.Result.Accepted;
import static io.servicetalk.client.api.RequestConcurrencyController.Result.RejectedPermanently;
import static io.servicetalk.client.api.RequestConcurrencyController.Result.RejectedTemporary;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Factory for common {@link ReservableRequestConcurrencyController}s.
 */
final class ReservableRequestConcurrencyControllers {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReservableRequestConcurrencyControllers.class);

    private ReservableRequestConcurrencyControllers() {
        // no instances
    }

    /**
     * Create a new instance of {@link ReservableRequestConcurrencyController}.
     *
     * @param maxConcurrency A {@link Publisher} that provides the maximum allowed concurrency updates.
     * @param onClosing A {@link Completable} that when terminated no more calls to
     * {@link RequestConcurrencyController#tryRequest()} are expected to succeed.
     * @param initialConcurrency The initial maximum value for concurrency, until {@code maxConcurrency} provides data.
     * @return a new instance of {@link ReservableRequestConcurrencyController}.
     */
    static ReservableRequestConcurrencyController newController(
            final Publisher<? extends ConsumableEvent<Integer>> maxConcurrency, final Completable onClosing,
            final int initialConcurrency) {
        return new ReservableRequestConcurrencyControllerMulti(maxConcurrency, onClosing, initialConcurrency);
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
        private volatile int lastMaxConcurrency;

        AbstractReservableRequestConcurrencyController(
                final Publisher<? extends ConsumableEvent<Integer>> maxConcurrency,
                final Completable onClosing,
                final int initialConcurrency) {
            lastMaxConcurrency = initialConcurrency;
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
            toSource(maxConcurrency).subscribe(new Subscriber<ConsumableEvent<Integer>>() {

                @Nullable
                private Subscription subscription;

                @Override
                public void onSubscribe(final Subscription s) {
                    if (checkDuplicateSubscription(subscription, s)) {
                        subscription = s;
                        s.request(1);
                    }
                }

                @Override
                public void onNext(@Nullable final ConsumableEvent<Integer> event) {
                    assert subscription != null : "Subscription can not be null in onNext.";
                    assert event != null : "event can not be null in onNext.";
                    final int currentConcurrency = lastMaxConcurrency;
                    final int newConcurrency = event.event();
                    if (currentConcurrency < newConcurrency) {
                        // When concurrency increases, consume event to notify Netty asap, then update the value to
                        // allow more requests to go through. Even if this event is offloaded, eventConsumed() will
                        // queue a task on Netty's event loop before any new requests come in from different threads.
                        // However, the race is still possible if only transportEventStream is offloaded, but requests
                        // are executed on the event loop. In this case, we rely on InternalRetryingHttpClientFilter.
                        event.eventConsumed();
                        lastMaxConcurrency = newConcurrency;
                    } else if (currentConcurrency > newConcurrency) {
                        // When concurrency decreases, update the value first to prevent new requests to go through,
                        // then consume the event. If concurrency decreases significantly, we may end up being in a
                        // situation when some requests already went through the controller and wait in the event loop
                        // queue to execute. In this case, netty will throw "Maximum active streams violated for this
                        // endpoint" exception, we rely on InternalRetryingHttpClientFilter to handle it.
                        // We can not defer eventConsumed until after the concurrency goes down, because all HTTP/2
                        // SETTINGS frames have to be acked in the order in which they are received. Waiting may affect
                        // ordering and ack may never be sent if users have long streaming requests.
                        lastMaxConcurrency = newConcurrency;
                        event.eventConsumed();
                    } else {
                        // No need to update the value because it is identical.
                        event.eventConsumed();
                    }
                    subscription.request(1);
                }

                @Override
                public void onError(final Throwable t) {
                    LOGGER.info("Unexpected error from transportEventStream(MAX_CONCURRENCY).", t);
                }

                @Override
                public void onComplete() {
                    LOGGER.debug("transportEventStream(MAX_CONCURRENCY) stream completes.");
                }
            });
        }

        @Override
        public final void requestFinished() {
            pendingRequestsUpdater.decrementAndGet(this);
        }

        @Override
        public final boolean tryReserve() {
            return pendingRequestsUpdater.compareAndSet(this, STATE_IDLE, STATE_RESERVED);
        }

        @Override
        public final Completable releaseAsync() {
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

        final int lastMaxConcurrency() {
            return lastMaxConcurrency;
        }

        final int pendingRequests() {
            return pendingRequests;
        }

        final boolean casPendingRequests(int oldValue, int newValue) {
            return pendingRequestsUpdater.compareAndSet(this, oldValue, newValue);
        }

        @Override
        public final String toString() {
            return getClass().getSimpleName() +
                    "{pendingRequests=" + pendingRequests +
                    ", lastMaxConcurrency=" + lastMaxConcurrency +
                    '}';
        }
    }

    private static final class ReservableRequestConcurrencyControllerMulti
            extends AbstractReservableRequestConcurrencyController {
        ReservableRequestConcurrencyControllerMulti(final Publisher<? extends ConsumableEvent<Integer>> maxConcurrency,
                                                    final Completable onClosing,
                                                    final int initialConcurrency) {
            super(maxConcurrency, onClosing, initialConcurrency);
        }

        @Override
        public Result tryRequest() {
            // We assume that frequency of changing `maxConcurrency` is low, while frequency of requests is high and can
            // cause CAS failure. We take `lastMaxConcurrency()` out of the loop to minimize number of volatile reads on
            // the hot path. In case there is a race between changing `maxConcurrency` to a lower value and a new
            // request, we rely on InternalRetryingHttpClientFilter to retry the request.
            final int maxConcurrency = lastMaxConcurrency();
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
