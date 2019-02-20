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
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkTerminationValidWithConcurrentOnNextCheck;
import static io.servicetalk.concurrent.internal.SubscriberUtils.sendOnNextWithConcurrentTerminationCheck;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.util.Objects.requireNonNull;

final class TakeUntilPublisher<T> extends AbstractSynchronousPublisherOperator<T, T> {

    private final Completable until;

    TakeUntilPublisher(Publisher<T> original, Completable until, Executor executor) {
        super(original, executor);
        this.until = requireNonNull(until);
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> subscriber) {
        return new TakeUntilSubscriber<>(subscriber, until);
    }

    private static final class TakeUntilSubscriber<T> implements Subscriber<T> {
        private static final Logger LOGGER = LoggerFactory.getLogger(TakeUntilSubscriber.class);
        private static final Object CANCELLED = new Object();
        private static final AtomicIntegerFieldUpdater<TakeUntilSubscriber> subscriberStateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(TakeUntilSubscriber.class, "subscriberState");
        private static final AtomicReferenceFieldUpdater<TakeUntilSubscriber, Object> terminalNotificationUpdater =
                AtomicReferenceFieldUpdater.newUpdater(TakeUntilSubscriber.class, Object.class, "terminalNotification");
        private static final AtomicReferenceFieldUpdater<TakeUntilSubscriber, Cancellable> untilCancellableUpdater =
                AtomicReferenceFieldUpdater.newUpdater(TakeUntilSubscriber.class, Cancellable.class, "untilCancellable");

        @SuppressWarnings("unused")
        private volatile int subscriberState;
        @SuppressWarnings("unused")
        @Nullable
        private volatile Object terminalNotification;
        @Nullable
        private volatile Subscription concurrentSubscription;
        @SuppressWarnings("unused")
        @Nullable
        private volatile Cancellable untilCancellable;

        private final io.servicetalk.concurrent.PublisherSource.Subscriber<? super T> subscriber;
        private final Completable until;

        TakeUntilSubscriber(Subscriber<? super T> subscriber, Completable until) {
            this.subscriber = subscriber;
            this.until = until;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            if (!checkDuplicateSubscription(concurrentSubscription, s)) {
                return;
            }
            final Subscription concurrentSubscription = new ConcurrentSubscription(s) {
                @Override
                public void cancel() {
                    super.cancel();
                    Cancellable untilCancellable =
                            untilCancellableUpdater.getAndSet(TakeUntilSubscriber.this, IGNORE_CANCEL);
                    if (untilCancellable != null) {
                        untilCancellable.cancel();
                    }
                }
            };
            this.concurrentSubscription = concurrentSubscription;
            subscriber.onSubscribe(concurrentSubscription);
            until.subscribe(new CompletableSource.Subscriber() {
                @Override
                public void onSubscribe(Cancellable cancellable) {
                    if (!untilCancellableUpdater.compareAndSet(TakeUntilSubscriber.this, null, cancellable)) {
                        cancellable.cancel();
                    }
                }

                @Override
                public void onComplete() {
                    if (checkTerminationValidWithConcurrentOnNextCheck(null, complete(), subscriberStateUpdater,
                            terminalNotificationUpdater, TakeUntilSubscriber.this)) {
                        // Call cancel on the actual Subscription that was passed into onSubscribe(...)
                        onComplete0();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (checkTerminationValidWithConcurrentOnNextCheck(null, t, subscriberStateUpdater,
                            terminalNotificationUpdater, TakeUntilSubscriber.this)) {
                        // Call cancel on the actual Subscription that was passed into onSubscribe(...)
                        onError0(t);
                    }
                }
            });
        }

        @Override
        public void onNext(T t) {
            sendOnNextWithConcurrentTerminationCheck(subscriber, t, this::terminate, subscriberStateUpdater,
                    terminalNotificationUpdater, this);
        }

        private void terminate(Object terminalNotification) {
            if (terminalNotification instanceof Throwable) {
                onError0((Throwable) terminalNotification);
            } else if (terminalNotification != CANCELLED) {
                onComplete0();
            }
        }

        @Override
        public void onError(Throwable t) {
            if (checkTerminationValidWithConcurrentOnNextCheck(null, t, subscriberStateUpdater,
                    terminalNotificationUpdater, this)) {
                onError0(t);
            } else {
                LOGGER.debug("onError ignored as the subscriber {} is already disposed.", this, t);
            }
        }

        void onError0(Throwable t) {
            invokeCancel();
            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            if (checkTerminationValidWithConcurrentOnNextCheck(null, complete(), subscriberStateUpdater,
                    terminalNotificationUpdater, this)) {
                onComplete0();
            }
        }

        void onComplete0() {
            invokeCancel();
            subscriber.onComplete();
        }

        private void invokeCancel() {
            Subscription concurrentSubscription = this.concurrentSubscription;
            assert concurrentSubscription != null;
            concurrentSubscription.cancel();
        }
    }
}
