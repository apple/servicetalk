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
import io.servicetalk.concurrent.internal.SignalOffloader;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class SingleConcatWithPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Single<? extends T> original;
    private final Publisher<? extends T> next;

    SingleConcatWithPublisher(final Single<? extends T> original, Publisher<? extends T> next, Executor executor) {
        this.original = original;
        this.next = next;
    }

    @Override
    Executor executor() {
        return original.executor();
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ConcatSubscriber<>(subscriber, next), signalOffloader, contextMap,
                contextProvider);
    }

    private static final class ConcatSubscriber<T> extends DelayedCancellableThenSubscription
            implements SingleSource.Subscriber<T>, Subscriber<T> {
        private static final Object INITIAL = new Object();
        private static final Object REQUESTED = new Object();
        private static final Object CANCELLED = new Object();
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<ConcatSubscriber, Object> mayBeResultUpdater =
                newUpdater(ConcatSubscriber.class, Object.class, "mayBeResult");

        private final Subscriber<? super T> target;
        private final Publisher<? extends T> next;

        /**
         * Following values are possible:
         * <ul>
         *     <li>{@link ConcatSubscriber#INITIAL} upon creation</li>
         *     <li>Actual result if {@link #onSuccess(Object)} invoked before {@link #request(long)}</li>
         *     <li>{@link ConcatSubscriber#REQUESTED} if {@link #request(long)} (with a valid n) invoked before
         *     {@link #onSuccess(Object)}</li>
         *     <li>{@link ConcatSubscriber#CANCELLED} if {@link #cancel()} is called or the first call to request(n)
         *     is invalid </li>
         * </ul>
         */
        @Nullable
        private volatile Object mayBeResult = INITIAL;

        ConcatSubscriber(final Subscriber<? super T> subscriber, final Publisher<? extends T> next) {
            this.target = subscriber;
            this.next = next;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            delayedCancellable(cancellable);
            target.onSubscribe(this);
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            for (;;) {
                Object oldValue = mayBeResult;
                if (oldValue == REQUESTED) {
                    emitSingleSuccessToTarget(result);
                    break;
                } else if (oldValue == CANCELLED || mayBeResultUpdater.compareAndSet(this, INITIAL, result)) {
                    break;
                }
            }
        }

        @Override
        public void onComplete() {
            target.onComplete();
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            delayedSubscription(subscription);
        }

        @Override
        public void onNext(@Nullable final T t) {
            target.onNext(t);
        }

        @Override
        public void onError(final Throwable t) {
            target.onError(t);
        }

        @Override
        public void request(long n) {
            for (;;) {
                Object oldVal = mayBeResult;
                if (oldVal == REQUESTED || oldVal == CANCELLED) {
                    super.request(n);
                    break;
                } else if (!isRequestNValid(n)) {
                    mayBeResult = CANCELLED;
                    try {
                        target.onError(newExceptionForInvalidRequestN(n));
                    } finally {
                        super.cancel();
                    }
                    break;
                } else if (mayBeResultUpdater.compareAndSet(this, oldVal, REQUESTED)) {
                    if (oldVal != INITIAL) {
                        @SuppressWarnings("unchecked")
                        final T tVal = (T) oldVal;
                        emitSingleSuccessToTarget(tVal);
                    }
                    if (n != 1) {
                        super.request(n - 1);
                    }
                    break;
                }
            }
        }

        @Override
        public void cancel() {
            // We track cancelled here because we need to make sure if cancel() happens subsequent calls to request(n)
            // are NOOPs [1].
            // [1] https://github.com/reactive-streams/reactive-streams-jvm#3.6
            mayBeResult = CANCELLED;
            super.cancel();
        }

        private void emitSingleSuccessToTarget(@Nullable final T result) {
            try {
                target.onNext(result);
            } catch (Throwable cause) {
                target.onError(cause);
                return;
            }
            next.subscribeInternal(this);
        }
    }
}
