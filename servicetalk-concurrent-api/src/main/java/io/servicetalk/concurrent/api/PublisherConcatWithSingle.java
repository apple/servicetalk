/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.FlowControlUtils;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.lang.Long.MIN_VALUE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * As returned by {@link Publisher#concat(Single)}.
 *
 * @param <T> Type of items emitted by this {@link Publisher}.
 */
final class PublisherConcatWithSingle<T> extends AbstractAsynchronousPublisherOperator<T, T> {
    private final Single<? extends T> next;

    PublisherConcatWithSingle(Publisher<T> original, Single<? extends T> next) {
        super(original);
        this.next = requireNonNull(next);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new ConcatSubscriber<>(subscriber, next);
    }

    private static final class ConcatSubscriber<T>
            implements SingleSource.Subscriber<T>, Subscriber<T>, Subscription {
        private static final Object CANCELLED = new Object();
        private static final Object TERMINATED = new Object();
        private static final AtomicReferenceFieldUpdater<ConcatSubscriber, Object> stateUpdater =
                newUpdater(ConcatSubscriber.class, Object.class, "state");
        private static final AtomicLongFieldUpdater<ConcatSubscriber> requestNUpdater =
                AtomicLongFieldUpdater.newUpdater(ConcatSubscriber.class, "requestN");
        private final Subscriber<? super T> target;
        private final Single<? extends T> next;
        private boolean nextSubscribed;

        @Nullable
        private volatile Object state;
        private volatile long requestN;

        ConcatSubscriber(Subscriber<? super T> target, Single<? extends T> next) {
            this.target = target;
            this.next = next;
        }

        @Override
        public void onSubscribe(Subscription s) {
            state = s;
            target.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            requestNUpdater.decrementAndGet(this);
            target.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            for (;;) {
                final Object s = state;
                if (s == CANCELLED) {
                    cancellable.cancel();
                    break;
                } else if (stateUpdater.compareAndSet(this, s, cancellable)) {
                    break;
                }
            }
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            // requestN may go negative as a result of this decrement. This is done to simplify underflow management
            // and is compensated for below and in request(n).
            long requestNBeforeDecrement = requestNUpdater.getAndDecrement(this);
            for (;;) {
                final Object s = state;
                if (s == CANCELLED || s == TERMINATED) {
                    break;
                } else if (requestNBeforeDecrement > 0) {
                    if (stateUpdater.compareAndSet(this, s, TERMINATED)) {
                        terminateTarget(result);
                        break;
                    }
                } else if (requestNBeforeDecrement == 0) {
                    if (stateUpdater.compareAndSet(this, s, new SingleResult<>(result))) {
                        if (s instanceof CancellableWithOutstandingDemand) {
                            // We have to re-check requestN here because it has changed.
                            requestNBeforeDecrement = requestN;
                            // CancellableWithOutstandingDemand means that requestN count has changed, it should not
                            // be 0 any more. We rely upon this because otherwise we may infinite loop here.
                            assert requestNBeforeDecrement != 0;
                        } else {
                            break;
                        }
                    }
                } else if (stateUpdater.compareAndSet(this, s, TERMINATED)) {
                    target.onError(newExceptionForInvalidRequestN(requestNBeforeDecrement));
                }
            }
        }

        @Override
        public void onComplete() {
            if (nextSubscribed) {
                target.onComplete();
            } else {
                nextSubscribed = true;
                next.subscribeInternal(this);
            }
        }

        @Override
        public void request(final long n) {
            final long requestNPostUpdate;
            if (isRequestNValid(n)) {
                requestNPostUpdate = requestNUpdater.accumulateAndGet(this, n,
                        FlowControlUtils::addWithOverflowProtectionIfGtEqNegativeOne);
            } else {
                requestNPostUpdate = sanitizeInvalidRequestN(n);
                requestN = requestNPostUpdate;
            }

            for (;;) {
                final Object s = state;
                if (s instanceof Subscription) {
                    ((Subscription) s).request(n);
                    break;
                } else if (s instanceof SingleResult) {
                    if (stateUpdater.compareAndSet(this, s, TERMINATED)) {
                        // requestNPostUpdate may be legitimately be 0 after incrementing because the single completion
                        // unconditionally decrements.
                        if (requestNPostUpdate >= 0) {
                            terminateTarget(SingleResult.fromRaw(s));
                        } else {
                            target.onError(newExceptionForInvalidRequestN(requestNPostUpdate));
                        }
                        break;
                    }
                } else if (s instanceof Cancellable && !(s instanceof CancellableWithOutstandingDemand)) {
                    if (stateUpdater.compareAndSet(this, s, new CancellableWithOutstandingDemand((Cancellable) s))) {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        @Override
        public void cancel() {
            for (;;) {
                final Object s = state;
                if (s == CANCELLED || s == TERMINATED) {
                    break;
                } else if (stateUpdater.compareAndSet(this, s, CANCELLED)) {
                    if (s instanceof Cancellable) {
                        ((Cancellable) s).cancel();
                    }
                    break;
                }
            }
        }

        private void terminateTarget(@Nullable final T t) {
            try {
                target.onNext(t);
            } catch (Throwable cause) {
                target.onError(cause);
                return;
            }
            target.onComplete();
        }

        private static long sanitizeInvalidRequestN(long n) {
            // 0 and -1 are used during arithmetic, but they are invalid. so just use -2. this also simplifies underflow
            // protection when decrementing.
            return n >= -1 ? -2 : n == MIN_VALUE ? MIN_VALUE + 1 : n;
        }
    }

    private static final class CancellableWithOutstandingDemand implements Cancellable {
        private final Cancellable cancellable;

        CancellableWithOutstandingDemand(final Cancellable cancellable) {
            this.cancellable = cancellable;
        }

        @Override
        public void cancel() {
            cancellable.cancel();
        }
    }

    private static final class SingleResult<T> {
        @Nullable
        private final T result;

        SingleResult(@Nullable final T result) {
            this.result = result;
        }

        @Nullable
        @SuppressWarnings("unchecked")
        static <T> T fromRaw(Object resultAsObject) {
            return ((SingleResult<T>) resultAsObject).result;
        }
    }
}
