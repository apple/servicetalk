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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

/**
 * As returned by {@link Publisher#concat(Completable)}.
 *
 * @param <T> Type of items emitted by this {@link Publisher}.
 */
final class PublisherConcatWithCompletable<T> extends AbstractAsynchronousPublisherOperator<T, T> {
    private final Completable next;
    private final boolean propagateCancel;

    PublisherConcatWithCompletable(Publisher<T> original, Completable next, boolean propagateCancel) {
        super(original);
        this.next = requireNonNull(next);
        this.propagateCancel = propagateCancel;
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return propagateCancel ?
                new ConcatSubscriberCancel<>(subscriber, next) : new ConcatSubscriber<>(subscriber, next);
    }

    private static final class ConcatSubscriberCancel<T>
            implements CompletableSource.Subscriber, PublisherSource.Subscriber<T>, Subscription {
        private static final Cancellable CANCELLED = () -> { };
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<ConcatSubscriberCancel, Cancellable> cancellableUpdater =
                newUpdater(ConcatSubscriberCancel.class, Cancellable.class, "cancellable");
        private final Subscriber<? super T> target;
        private final Completable next;
        private volatile Cancellable cancellable = IGNORE_CANCEL;

        ConcatSubscriberCancel(Subscriber<? super T> target, Completable next) {
            this.target = target;
            this.next = next;
        }

        @Override
        public void onSubscribe(Subscription s) {
            cancellable = new FirstSubscription(s);
            target.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            target.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            for (;;) {
                final Cancellable c = cancellable;
                assert c != IGNORE_CANCEL;
                if (FirstSubscription.class.equals(c.getClass())) {
                    if (cancellableUpdater.compareAndSet(this, c, CANCELLED)) {
                        try {
                            target.onError(t);
                        } finally {
                            next.subscribeInternal(this);
                        }
                        break;
                    }
                } else if (c == CANCELLED) {
                    // CANCELLED means downstream doesn't care about being notified and internal state tracking also
                    // uses this state if terminal has been propagated, so avoid duplicate terminal. We may subscribe
                    // to both sources in parallel if cancellation occurs, so allowing terminal to propagate would mean
                    // ordering and concurrency needs to be accounted for between Publisher and completed, because
                    // cancel allows for no more future delivery we avoid future invocation of the target subscriber.
                    break;
                } else if (cancellableUpdater.compareAndSet(this, c, CANCELLED)) {
                    target.onError(t);
                    break;
                }
            }
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            for (;;) {
                final Cancellable c = this.cancellable;
                if (c == CANCELLED) {
                    cancellable.cancel();
                    break;
                } else if (cancellableUpdater.compareAndSet(this, c, cancellable)) {
                    break;
                }
            }
        }

        @Override
        public void onComplete() {
            for (;;) {
                final Cancellable c = cancellable;
                assert c != IGNORE_CANCEL;
                if (FirstSubscription.class.equals(c.getClass())) {
                    if (cancellableUpdater.compareAndSet(this, c, IGNORE_CANCEL)) {
                        next.subscribeInternal(this);
                        break;
                    }
                } else if (c == CANCELLED) {
                    // CANCELLED means downstream doesn't care about being notified and internal state tracking also
                    // uses this state if terminal has been propagated, so avoid duplicate terminal. We may subscribe
                    // to both sources in parallel if cancellation occurs, so allowing terminal to propagate would mean
                    // ordering and concurrency needs to be accounted for between Publisher and completed, because
                    // cancel allows for no more future delivery we avoid future invocation of the target subscriber.
                    break;
                } else if (cancellableUpdater.compareAndSet(this, c, CANCELLED)) {
                    target.onComplete();
                    break;
                }
            }
        }

        @Override
        public void request(final long n) {
            Cancellable currCancellable = cancellable;
            if (FirstSubscription.class.equals(currCancellable.getClass())) {
                ((FirstSubscription) currCancellable).request(n);
            }
        }

        @Override
        public void cancel() {
            final Cancellable c = cancellableUpdater.getAndSet(this, CANCELLED);
            try {
                c.cancel();
            } finally {
                if (FirstSubscription.class.equals(c.getClass())) {
                    next.subscribeInternal(this);
                }
            }
        }

        private static final class FirstSubscription implements Subscription {
            private final Subscription subscription;

            private FirstSubscription(final Subscription subscription) {
                this.subscription = subscription;
            }

            @Override
            public void cancel() {
                subscription.cancel();
            }

            @Override
            public void request(final long n) {
                subscription.request(n);
            }
        }
    }

    private static final class ConcatSubscriber<T>
            implements CompletableSource.Subscriber, PublisherSource.Subscriber<T>, Subscription {
        private static final Cancellable CANCELLED = () -> { };
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<ConcatSubscriber, Cancellable> cancellableUpdater =
                newUpdater(ConcatSubscriber.class, Cancellable.class, "cancellable");
        private final Subscriber<? super T> target;
        private final Completable next;
        private boolean nextSubscribed;

        private volatile Cancellable cancellable = IGNORE_CANCEL;

        ConcatSubscriber(Subscriber<? super T> target, Completable next) {
            this.target = target;
            this.next = next;
        }

        @Override
        public void onSubscribe(Subscription s) {
            cancellable = s;
            target.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            target.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            for (;;) {
                final Cancellable c = this.cancellable;
                if (c == CANCELLED) {
                    cancellable.cancel();
                    break;
                } else if (cancellableUpdater.compareAndSet(this, c, cancellable)) {
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
            Cancellable currCancellable = cancellable;
            if (currCancellable instanceof Subscription) {
                ((Subscription) currCancellable).request(n);
            }
        }

        @Override
        public void cancel() {
            cancellableUpdater.getAndSet(this, CANCELLED).cancel();
        }
    }
}
