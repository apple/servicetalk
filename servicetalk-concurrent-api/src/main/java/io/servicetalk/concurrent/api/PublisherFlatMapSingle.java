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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.FlowControlUtil;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.ConcurrentUtils.drainSingleConsumerQueue;
import static io.servicetalk.concurrent.internal.PlatformDependent.newUnboundedMpscQueue;
import static io.servicetalk.concurrent.internal.SubscriberUtils.NULL_TOKEN;
import static io.servicetalk.concurrent.internal.SubscriberUtils.calculateSourceRequested;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.trySetTerminal;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * As returned by {@link Publisher#flatMapSingle(Function, int)} and its variants.
 *
 * @param <R> Type of items emitted by this {@link Publisher}
 * @param <T> Type of items emitted by source {@link Publisher}
 */
final class PublisherFlatMapSingle<T, R> extends AbstractAsynchronousPublisherOperator<T, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublisherFlatMapSingle.class);

    private final Function<? super T, Single<? extends R>> mapper;
    private final int maxConcurrency;
    private final boolean delayError;

    PublisherFlatMapSingle(Publisher<T> original, Function<? super T, Single<? extends R>> mapper, boolean delayError,
                           Executor executor) {
        this(original, mapper, 16, delayError, executor);
    }

    PublisherFlatMapSingle(Publisher<T> original, Function<? super T, Single<? extends R>> mapper, int maxConcurrency,
                           boolean delayError, Executor executor) {
        super(original, executor);
        this.mapper = requireNonNull(mapper);
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency: " + maxConcurrency + " (expected > 0)");
        }
        this.maxConcurrency = maxConcurrency;
        this.delayError = delayError;
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super R> subscriber) {
        return new FlatMapSubscriber<>(this, subscriber);
    }

    private static final class FlatMapSubscriber<T, R> implements org.reactivestreams.Subscriber<T>, Subscription {
        private static final AtomicReferenceFieldUpdater<FlatMapSubscriber, CompositeException> delayedErrorUpdater =
                newUpdater(FlatMapSubscriber.class, CompositeException.class, "delayedError");
        private static final AtomicIntegerFieldUpdater<FlatMapSubscriber> emittingUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapSubscriber.class, "emitting");
        private static final AtomicLongFieldUpdater<FlatMapSubscriber> requestedUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapSubscriber.class, "requested");
        private static final AtomicLongFieldUpdater<FlatMapSubscriber> sourceRequestedUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapSubscriber.class, "sourceRequested");
        private static final AtomicLongFieldUpdater<FlatMapSubscriber> sourceEmittedUpdater =
                AtomicLongFieldUpdater.newUpdater(FlatMapSubscriber.class, "sourceEmitted");
        private static final AtomicIntegerFieldUpdater<FlatMapSubscriber> activeUpdater =
                AtomicIntegerFieldUpdater.newUpdater(FlatMapSubscriber.class, "active");
        private static final AtomicReferenceFieldUpdater<FlatMapSubscriber, TerminalNotification>
                terminalNotificationUpdater = newUpdater(FlatMapSubscriber.class, TerminalNotification.class,
                "terminalNotification");

        @SuppressWarnings("unused")
        @Nullable
        private volatile CompositeException delayedError;
        @SuppressWarnings("unused")
        private volatile int emitting;
        @SuppressWarnings("unused")
        private volatile long requested;
        @SuppressWarnings("unused")
        private volatile long sourceEmitted;
        @SuppressWarnings("unused")
        private volatile long sourceRequested;
        @SuppressWarnings("unused")
        private volatile int active; // Number of currently active Singles.
        @SuppressWarnings("unused")
        @Nullable
        private volatile Subscription subscription;
        @SuppressWarnings("unused")
        @Nullable
        private volatile TerminalNotification terminalNotification;
        /**
         * This variable is only accessed within the "emitting lock" so we rely upon this to provide visibility to
         * other threads.
         */
        private boolean targetTerminated;

        private final Queue<Object> pending;
        private final DynamicCompositeCancellable cancellable = new MapDynamicCompositeCancellable();
        private final PublisherFlatMapSingle<T, R> source;
        private final org.reactivestreams.Subscriber<? super R> target;

        /*
         * An indicator in the pending queue that a Single terminated with error.
         */
        private static final Object SINGLE_ERROR = new Object();

        FlatMapSubscriber(PublisherFlatMapSingle<T, R> source, org.reactivestreams.Subscriber<? super R> target) {
            this.source = source;
            this.target = target;
            // Start with a small capacity as maxConcurrency can be large.
            pending = newUnboundedMpscQueue(min(2, source.maxConcurrency));
        }

        @Override
        public void request(long n) {
            final Subscription s = subscription;
            assert s != null;
            if (!isRequestNValid(n)) {
                s.request(n);
                return;
            }

            requestedUpdater.accumulateAndGet(this, n, FlowControlUtil::addWithOverflowProtection);
            int actualSourceRequestN = calculateSourceRequested(requestedUpdater, sourceRequestedUpdater,
                    sourceEmittedUpdater, source.maxConcurrency, this);
            if (actualSourceRequestN != 0) {
                s.request(actualSourceRequestN);
            }
        }

        @Override
        public void cancel() {
            doCancel(true);
        }

        @Override
        public void onSubscribe(Subscription s) {
            // Subscription is volatile, but there will be no access to the subscription until we call onSubscribe below
            // so we don't have to worry about atomic operations here.
            if (!checkDuplicateSubscription(subscription, s)) {
                return;
            }
            // We assume that FlatMapSubscriber#cancel() will never be called before this method, and therefore we
            // don't have to worry about being cancelled before the onSubscribe method is called.
            subscription = ConcurrentSubscription.wrap(s);
            target.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            final Single<? extends R> next = requireNonNull(source.mapper.apply(t));

            // Requested count will be decremented after this single completes.
            // If we are cancelled the activeUpdater count is best effort depending upon which sources finish. This best
            // effort behavior mimics the semantics of cancel though so we don't take any special action to try to
            // adjust the count or prematurely terminate.
            activeUpdater.incrementAndGet(this);
            next.subscribe(new FlatMapSingleSubscriber());
        }

        @Override
        public void onError(Throwable t) {
            if (!onError0(t, false, false)) {
                LOGGER.debug("Already terminated/cancelled, ignoring error notification.", t);
            }
        }

        @Override
        public void onComplete() {
            // active must be checked after setting the terminal event, because they are accessed in the reverse way in
            // FlatMapSingleSubscriber and if it were reversed here the FlatMapSingleSubscriber would be racy and may
            // not detect the terminal event.
            if (trySetTerminal(complete(), false, terminalNotificationUpdater, this) && active == 0) {
                // Since onComplete and onNext can not be concurrent and onNext must not be invoked post onComplete,
                // if we see active == 0 here, active must not change after this.
                enqueueAndDrain(complete());
            }
        }

        private boolean onError0(Throwable throwable, boolean overrideComplete,
                                 boolean cancelSubscriberIfNecessary) {
            final TerminalNotification notification = TerminalNotification.error(throwable);
            if (trySetTerminal(notification, overrideComplete, terminalNotificationUpdater, this)) {
                try {
                    doCancel(cancelSubscriberIfNecessary);
                } finally {
                    enqueueAndDrain(notification);
                }
                return true;
            }
            return false;
        }

        private void enqueueAndDrain(Object item) {
            Subscription s = subscription;
            assert s != null;

            if (!pending.offer(item)) {
                IllegalStateException exception =
                        new IllegalStateException("Unexpected reject from pending queue while enqueuing item: " + item);
                if (item instanceof TerminalNotification) {
                    LOGGER.error("Queue should be unbounded, but an offer failed!", exception);
                    throw exception;
                } else {
                    onError0(exception, true, true);
                }
            }
            drainPending(s);
        }

        private void drainPending(Subscription subscription) {
            long drainedCount = drainSingleConsumerQueue(pending, this::sendToTarget, emittingUpdater, this);
            if (drainedCount != 0) {
                // We ignore overflow here because once we get to this extreme, we won't be able to account for more
                // data anyways.
                sourceEmittedUpdater.addAndGet(this, drainedCount);
                int actualSourceRequestN = calculateSourceRequested(requestedUpdater, sourceRequestedUpdater,
                        sourceEmittedUpdater, source.maxConcurrency, this);
                if (actualSourceRequestN != 0) {
                    subscription.request(actualSourceRequestN);
                }
            }
        }

        /**
         * Cancel and cleanup.
         * @param cancelSubscription enforces the
         * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.1/README.md#2.3">
         *     reactive streams rule 2.3</a>.
         */
        private void doCancel(boolean cancelSubscription) {
            cancellable.cancel();
            if (cancelSubscription) {
                Subscription subscription = this.subscription;
                assert subscription != null;
                subscription.cancel();
            }
        }

        private void sendToTarget(Object item) {
            if (targetTerminated || item == SINGLE_ERROR) {
                // No notifications past terminal/cancelled
                return;
            }
            if (item instanceof TerminalNotification) {
                targetTerminated = true;
                // Load the terminal notification in case an error happened after an onComplete and we override the
                // terminal value.
                TerminalNotification terminalNotification = this.terminalNotification;
                assert terminalNotification != null;
                CompositeException de = this.delayedError;
                if (de != null) {
                    de.addAllPendingSuppressed();
                    if (terminalNotification.getCause() == de) {
                        terminalNotification.terminate(target);
                    } else {
                        terminalNotification.terminate(target, de);
                    }
                } else {
                    terminalNotification.terminate(target);
                }
            } else if (item == NULL_TOKEN) {
                target.onNext(null);
            } else {
                @SuppressWarnings("unchecked")
                final R rItem = (R) item;
                target.onNext(rItem);
            }
        }

        private final class FlatMapSingleSubscriber implements Single.Subscriber<R> {
            @Nullable
            private Cancellable singleCancellable;

            @Override
            public void onSubscribe(Cancellable singleCancellable) {
                // It is possible we have been cancelled at this point, and cancellable/FlatMapSingleSubscriber will
                // take care of propagating the cancel to next. The activeUpdater count is now best effort depending
                // upon which sources finish. This best effort behavior mimics the semantics of cancel though so we
                // don't take any special action to try to adjust the count or prematurely terminate.
                this.singleCancellable = singleCancellable;
                cancellable.add(singleCancellable);
            }

            @Override
            public void onSuccess(@Nullable R result) {
                // First enqueue the result and then decrement active count. Since onComplete() checks for active count,
                // if we decrement count before enqueuing, onComplete() may emit the terminal event without emitting
                // the result.
                enqueueAndDrain(result == null ? NULL_TOKEN : result);
                if (onSingleTerminated()) {
                    enqueueAndDrain(complete());
                }
            }

            @Override
            public void onError(Throwable t) {
                if (!source.delayError) {
                    onError0(t, true, true);
                } else {
                    CompositeException de = FlatMapSubscriber.this.delayedError;
                    if (de == null) {
                        de = new CompositeException(t);
                        if (!delayedErrorUpdater.compareAndSet(FlatMapSubscriber.this, null, de)) {
                            de = FlatMapSubscriber.this.delayedError;
                            assert de != null;
                            de.add(t);
                        }
                    } else {
                        de.add(t);
                    }
                    if (onSingleTerminated()) {
                        if (trySetTerminal(TerminalNotification.error(de), true, terminalNotificationUpdater,
                                FlatMapSubscriber.this)) {
                            // Since we have already added error to delayedError, we use complete() TerminalNotification
                            // as a dummy signal to start draining and termination.
                            enqueueAndDrain(complete());
                        }
                    } else {
                        // Queueing/draining may result in requestN more data.
                        enqueueAndDrain(SINGLE_ERROR);
                    }
                }
            }

            private boolean onSingleTerminated() {
                assert singleCancellable != null;
                cancellable.remove(singleCancellable);
                // The ordering of events is important here. If this changes then onComplete must also change otherwise
                // there is a race condition.
                return activeUpdater.decrementAndGet(FlatMapSubscriber.this) == 0 && terminalNotification != null;
            }
        }
    }
}
