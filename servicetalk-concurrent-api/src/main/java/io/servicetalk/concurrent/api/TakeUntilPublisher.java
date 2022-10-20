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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.ConcurrentTerminalSubscriber;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static java.util.Objects.requireNonNull;

final class TakeUntilPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {
    private final Supplier<? extends Completable> until;

    TakeUntilPublisher(Publisher<T> original, Supplier<? extends Completable> until) {
        super(original);
        this.until = requireNonNull(until);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new TakeUntilSubscriber<>(subscriber, until.get());
    }

    private static final class TakeUntilSubscriber<T> implements Subscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<TakeUntilSubscriber, Cancellable> untilCancellableUpdater =
                AtomicReferenceFieldUpdater.newUpdater(TakeUntilSubscriber.class, Cancellable.class,
                        "untilCancellable");
        @Nullable
        private volatile TakeUntilSubscription downstreamSubscription;
        @SuppressWarnings("unused")
        @Nullable
        private volatile Cancellable untilCancellable;

        private final ConcurrentTerminalSubscriber<? super T> subscriber;
        private final Completable until;

        TakeUntilSubscriber(Subscriber<? super T> subscriber, Completable until) {
            this.subscriber = new ConcurrentTerminalSubscriber<>(subscriber, false);
            this.until = until;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            if (!checkDuplicateSubscription(downstreamSubscription, s)) {
                return;
            }
            final TakeUntilSubscription takeSubscription = new TakeUntilSubscription(s, this::cancelUntil);
            this.downstreamSubscription = takeSubscription;
            subscriber.onSubscribe(takeSubscription);
            until.subscribeInternal(new CompletableSource.Subscriber() {
                @Override
                public void onSubscribe(Cancellable cancellable) {
                    if (!untilCancellableUpdater.compareAndSet(TakeUntilSubscriber.this, null, cancellable)) {
                        cancellable.cancel();
                    }
                }

                @Override
                public void onComplete() {
                    try {
                        cancelDownstreamSubscription();
                    } finally {
                        subscriber.processOnComplete();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    try {
                        cancelDownstreamSubscription();
                    } finally {
                        subscriber.processOnError(t);
                    }
                }

                private void cancelDownstreamSubscription() {
                    TakeUntilSubscription s = downstreamSubscription;
                    assert s != null;
                    s.superCancel();
                }
            });
        }

        @Override
        public void onNext(T t) {
            subscriber.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            try {
                cancelUntil();
            } finally {
                subscriber.processOnError(t);
            }
        }

        @Override
        public void onComplete() {
            try {
                cancelUntil();
            } finally {
                subscriber.processOnComplete();
            }
        }

        private void cancelUntil() {
            Cancellable untilCancellable =
                    untilCancellableUpdater.getAndSet(TakeUntilSubscriber.this, IGNORE_CANCEL);
            if (untilCancellable != null) {
                untilCancellable.cancel();
            }
        }
    }

    private static final class TakeUntilSubscription extends ConcurrentSubscription {
        private final Cancellable cancellable;

        private TakeUntilSubscription(final Subscription subscription, Cancellable cancellable) {
            super(subscription);
            this.cancellable = cancellable;
        }

        @Override
        public void cancel() {
            try {
                super.cancel();
            } finally {
                cancellable.cancel();
            }
        }

        void superCancel() {
            super.cancel();
        }
    }
}
