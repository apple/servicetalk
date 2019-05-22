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
import io.servicetalk.concurrent.internal.SubscriberUtils;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.FlowControlUtil.addWithOverflowProtection;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * As returned by {@link Publisher#concat(Single)}.
 *
 * @param <T> Type of items emitted by this {@link Publisher}.
 */
final class PublisherConcatWithSingle<T> extends AbstractAsynchronousPublisherOperator<T, T> {
    private final Single<? extends T> next;

    PublisherConcatWithSingle(Publisher<T> original, Single<? extends T> next, Executor executor) {
        super(original, executor);
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
        private final Subscriber<? super T> target;
        private final Single<? extends T> next;
        private boolean nextSubscribed;

        @Nullable
        private Subscription subscription;

        /**
         * Can have the following values:
         * <ul>
         *     <li>{@code null} at creation.</li>
         *     <li>{@link Long} representing pending requested items.</li>
         *     <li>{@link ConcatSubscriber#CANCELLED} once {@link #cancel()} is called, after this state will never
         *     change.</li>
         *     <li>{@link Cancellable} once {@link #onSubscribe(Cancellable)} and {@link #cancel()} has not been
         *     called.</li>
         *     <li>{@code Result} of the next {@link Single} when available.</li>
         * </ul>
         */
        @Nullable
        private volatile Object state;

        ConcatSubscriber(Subscriber<? super T> target, Single<? extends T> next) {
            this.target = target;
            this.next = next;
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            target.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            for (;;) {
                final Object s = state;
                if (s == CANCELLED) {
                    return;
                }
                assert s instanceof Long;
                if (stateUpdater.compareAndSet(this, s, ((long) s - 1))) {
                    break;
                }
            }
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
                    return;
                }
                if (s instanceof Long && stateUpdater.compareAndSet(this, s,
                        (long) s > 0 ? new CancellableWithOutstandingDemand(cancellable) : cancellable)
                        || s == null && stateUpdater.compareAndSet(this, null, cancellable)) {
                    break;
                }
            }
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            for (;;) {
                final Object s = state;
                if (s == CANCELLED || s == TERMINATED) {
                    return;
                }
                if (s instanceof PublisherConcatWithSingle.CancellableWithOutstandingDemand) {
                    if (stateUpdater.compareAndSet(this, s, TERMINATED)) {
                        terminateTarget(result);
                        break;
                    }
                } else if (stateUpdater.compareAndSet(this, s, new SingleResult<>(result))) {
                    break;
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
            assert subscription != null;
            if (!SubscriberUtils.isRequestNValid(n)) {
                subscription.request(n);
                return;
            }

            for (;;) {
                final Object s = state;
                if (s == CANCELLED || s == TERMINATED) {
                    return;
                }
                if (s instanceof Long || s == null) {
                    long nextState = s != null ? addWithOverflowProtection((long) s, n) : n;
                    if (stateUpdater.compareAndSet(this, s, nextState)) {
                        subscription.request(n);
                        return;
                    }
                } else if (s instanceof PublisherConcatWithSingle.CancellableWithOutstandingDemand) {
                    // already requested
                    return;
                } else if (s instanceof Cancellable) {
                    if (stateUpdater.compareAndSet(this, s, new CancellableWithOutstandingDemand((Cancellable) s))) {
                        return;
                    }
                } else {
                    assert s instanceof PublisherConcatWithSingle.SingleResult;
                    if (stateUpdater.compareAndSet(this, s, TERMINATED)) {
                        terminateTarget(SingleResult.fromRaw(s));
                        return;
                    }
                }
            }
        }

        @Override
        public void cancel() {
            assert subscription != null;
            for (;;) {
                final Object s = state;
                if (s == CANCELLED || s == TERMINATED) {
                    return;
                }
                if (stateUpdater.compareAndSet(this, s, CANCELLED)) {
                    if (s instanceof Cancellable) {
                        ((Cancellable) s).cancel();
                    } else {
                        subscription.cancel();
                    }
                    break;
                }
            }
        }

        private void terminateTarget(@Nullable final T t) {
            target.onNext(t);
            target.onComplete();
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
