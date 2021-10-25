/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.context.api.ContextMap;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class SingleConcatWithPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Single<? extends T> original;
    private final Publisher<? extends T> next;
    private final boolean deferSubscribe;

    SingleConcatWithPublisher(final Single<? extends T> original, final Publisher<? extends T> next,
                              final boolean deferSubscribe) {
        this.original = original;
        this.next = Objects.requireNonNull(next, "next");
        this.deferSubscribe = deferSubscribe;
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber,
                         final ContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(deferSubscribe ? new ConcatDeferNextSubscriber<>(subscriber, next) :
                        new ConcatSubscriber<>(subscriber, next), contextMap, contextProvider);
    }

    private abstract static class AbstractConcatSubscriber<T> extends DelayedCancellableThenSubscription
            implements SingleSource.Subscriber<T>, Subscriber<T> {

        /**
         * Initial state upon creation.
         */
        static final Object INITIAL = new Object();
        /**
         * If {@link #cancel()} is called, the first call to request(n) is invalid, or the terminal signal was already
         * delivered.
         */
        static final Object CANCELLED = new Object();

        @SuppressWarnings("rawtypes")
        static final AtomicReferenceFieldUpdater<AbstractConcatSubscriber, Object> mayBeResultUpdater =
                newUpdater(AbstractConcatSubscriber.class, Object.class, "mayBeResult");

        final Subscriber<? super T> target;
        final Publisher<? extends T> next;

        /**
         * It may be the actual result if {@link #onSuccess(Object)} invoked before {@link #request(long)}.
         */
        @Nullable
        volatile Object mayBeResult = INITIAL;

        AbstractConcatSubscriber(final Subscriber<? super T> target, final Publisher<? extends T> next) {
            this.target = target;
            this.next = next;
        }

        @Override
        public final void onSubscribe(final Cancellable cancellable) {
            delayedCancellable(cancellable);
            target.onSubscribe(this);
        }

        @Override
        public final void onSubscribe(final Subscription subscription) {
            delayedSubscription(subscription);
        }

        @Override
        public final void onNext(@Nullable final T t) {
            target.onNext(t);
        }

        @Override
        public final void onError(final Throwable t) {
            target.onError(t);
        }

        @Override
        public final void onComplete() {
            target.onComplete();
        }

        @Override
        public final void cancel() {
            // We track cancelled here because we need to make sure if cancel() happens subsequent calls to request(n)
            // are NOOPs [1].
            // [1] https://github.com/reactive-streams/reactive-streams-jvm#3.6
            mayBeResult = CANCELLED;
            super.cancel();
        }

        /**
         * Helper method to invoke {@link DelayedCancellableThenSubscription#cancel()} from subclasses.
         */
        final void superCancel() {
            super.cancel();
        }

        final boolean tryEmitSingleSuccessToTarget(@Nullable final T result) {
            try {
                target.onNext(result);
                return true;
            } catch (Throwable cause) {
                mayBeResult = CANCELLED;
                target.onError(cause);
                return false;
            }
        }
    }

    private static final class ConcatSubscriber<T> extends AbstractConcatSubscriber<T> {
        /**
         * If {@link #request(long)} (with a valid n) invoked before {@link #onSuccess(Object)}.
         */
        private static final Object REQUESTED = new Object();

        ConcatSubscriber(final Subscriber<? super T> target, final Publisher<? extends T> next) {
            super(target, next);
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            for (;;) {
                final Object oldValue = mayBeResult;
                if (oldValue == REQUESTED) {
                    if (tryEmitSingleSuccessToTarget(result)) {
                        next.subscribeInternal(this);
                    }
                    break;
                } else if (oldValue == CANCELLED || mayBeResultUpdater.compareAndSet(this, INITIAL, result)) {
                    break;
                }
            }
        }

        @Override
        public void request(long n) {
            for (;;) {
                final Object oldVal = mayBeResult;
                if (oldVal == CANCELLED) {
                    break;
                } else if (oldVal == REQUESTED) {
                    super.request(n);
                    break;
                } else if (!isRequestNValid(n)) {
                    mayBeResult = CANCELLED;
                    try {
                        target.onError(newExceptionForInvalidRequestN(n));
                    } finally {
                        superCancel();
                    }
                    break;
                } else if (mayBeResultUpdater.compareAndSet(this, oldVal, REQUESTED)) {
                    // We need to ensure that the queued result is delivered in order (first). Upstream demand is
                    // delayed via DelayedSubscription until onSubscribe which preserves ordering, and there are some
                    // scenarios where subscribing to the concat Publisher may block on demand (e.g.
                    // ConnectablePayloadWriter write) so we need to propagate demand first to prevent deadlock.
                    if (n != 1) {
                        super.request(n - 1);
                    }

                    if (oldVal != INITIAL) {
                        @SuppressWarnings("unchecked")
                        final T tVal = (T) oldVal;
                        if (tryEmitSingleSuccessToTarget(tVal)) {
                            next.subscribeInternal(this);
                        }
                    }
                    break;
                }
            }
        }
    }

    private static final class ConcatDeferNextSubscriber<T> extends AbstractConcatSubscriber<T> {
        /**
         * If only one item was {@link #request(long) requested} before {@link #onSuccess(Object)}.
         */
        private static final Object REQUESTED_ONE = new Object();
        /**
         * If more than one item was {@link #request(long) requested} before {@link #onSuccess(Object)} or while its
         * result is delivering to the target.
         */
        private static final Object REQUESTED_MORE = new Object();
        /**
         * If only one item was {@link #request(long) requested} and {@link #onSuccess(Object)} invoked.
         */
        private static final Object SINGLE_DELIVERING = new Object();
        /**
         * If only one item was {@link #request(long) requested}, {@link #onSuccess(Object)} invoked, and its result was
         * delivered to the target.
         */
        private static final Object SINGLE_DELIVERED = new Object();
        /**
         * If more than one item was {@link #request(long) requested}, {@link #onSuccess(Object)} invoked, and we
         * subscribed to the next {@link Publisher}.
         */
        private static final Object PUBLISHER_SUBSCRIBED = new Object();

        ConcatDeferNextSubscriber(final Subscriber<? super T> target, final Publisher<? extends T> next) {
            super(target, next);
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            for (;;) {
                final Object oldValue = mayBeResult;
                assert oldValue != SINGLE_DELIVERING;
                assert oldValue != SINGLE_DELIVERED;
                assert oldValue != PUBLISHER_SUBSCRIBED;

                if (oldValue == CANCELLED) {
                    break;
                } else if (oldValue == INITIAL) {
                    if (mayBeResultUpdater.compareAndSet(this, oldValue, result)) {
                        break;
                    }
                } else if (oldValue == REQUESTED_ONE) {
                    if (mayBeResultUpdater.compareAndSet(this, oldValue, SINGLE_DELIVERING)) {
                        emitSingleSuccessToTarget(result);
                        break;
                    }
                } else if (oldValue == REQUESTED_MORE &&
                        mayBeResultUpdater.compareAndSet(this, oldValue, PUBLISHER_SUBSCRIBED)) {
                    if (tryEmitSingleSuccessToTarget(result)) {
                        next.subscribeInternal(this);
                    }
                    break;
                }
            }
        }

        @Override
        public void request(long n) {
            for (;;) {
                final Object oldVal = mayBeResult;
                if (oldVal == CANCELLED) {
                    break;
                } else if (oldVal == PUBLISHER_SUBSCRIBED || oldVal == REQUESTED_MORE) {
                    super.request(n);
                    break;
                } else if (!isRequestNValid(n)) {
                    mayBeResult = CANCELLED;
                    try {
                        target.onError(newExceptionForInvalidRequestN(n));
                    } finally {
                        superCancel();
                    }
                    break;
                } else if (oldVal == INITIAL) {
                    if (n > 1) {
                        if (mayBeResultUpdater.compareAndSet(this, oldVal, REQUESTED_MORE)) {
                            super.request(n - 1);
                            break;
                        }
                    } else {
                        assert n == 1;
                        if (mayBeResultUpdater.compareAndSet(this, oldVal, REQUESTED_ONE)) {
                            break;
                        }
                    }
                } else if (oldVal == REQUESTED_ONE || oldVal == SINGLE_DELIVERING) {
                    if (mayBeResultUpdater.compareAndSet(this, oldVal, REQUESTED_MORE)) {
                        super.request(n);
                        break;
                    }
                } else if (oldVal == SINGLE_DELIVERED) {
                    if (mayBeResultUpdater.compareAndSet(this, oldVal, PUBLISHER_SUBSCRIBED)) {
                        super.request(n);
                        next.subscribeInternal(this);
                        break;
                    }
                } else if (n > 1) {
                    if (mayBeResultUpdater.compareAndSet(this, oldVal, PUBLISHER_SUBSCRIBED)) {
                        @SuppressWarnings("unchecked")
                        final T tVal = (T) oldVal;
                        if (tryEmitSingleSuccessToTarget(tVal)) {
                            super.request(n - 1);
                            next.subscribeInternal(this);
                        }
                        break;
                    }
                } else if (mayBeResultUpdater.compareAndSet(this, oldVal, SINGLE_DELIVERING)) {
                    @SuppressWarnings("unchecked")
                    final T tVal = (T) oldVal;
                    emitSingleSuccessToTarget(tVal);
                    break;
                }
            }
        }

        private void emitSingleSuccessToTarget(@Nullable final T result) {
            if (tryEmitSingleSuccessToTarget(result)) {
                if (mayBeResultUpdater.compareAndSet(this, SINGLE_DELIVERING, SINGLE_DELIVERED)) {
                    // state didn't change, we are done
                } else if (mayBeResultUpdater.compareAndSet(this, REQUESTED_MORE, PUBLISHER_SUBSCRIBED)) {
                    // more demand appeared while we were delivering the single result
                    next.subscribeInternal(this);
                } else {
                    assert mayBeResult == CANCELLED;
                }
            }
        }
    }
}
