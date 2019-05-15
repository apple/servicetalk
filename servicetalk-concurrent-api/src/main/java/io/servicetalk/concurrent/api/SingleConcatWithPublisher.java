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

import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;

final class SingleConcatWithPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final Single<? extends T> original;
    private final Publisher<? extends T> next;

    SingleConcatWithPublisher(final Single<? extends T> original, Publisher<? extends T> next, Executor executor) {
        super(executor);
        this.original = original;
        this.next = next;
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        original.delegateSubscribe(new ConcatSubscriber<>(subscriber, next), signalOffloader, contextMap,
                contextProvider);
    }

    private static final class ConcatSubscriber<T> extends CancellableThenSubscription
            implements SingleSource.Subscriber<T>, Subscriber<T> {
        private static final Object INITIAL_VALUE = new Object();
        private static final Object REQUESTED = new Object();
        private static final AtomicReferenceFieldUpdater<ConcatSubscriber, Object> mayBeResultUpdater =
                newUpdater(ConcatSubscriber.class, Object.class, "mayBeResult");

        private final Subscriber<? super T> target;
        private final Publisher<? extends T> next;

        /**
         * Following values are possible:
         * <ul>
         *     <li>{@link ConcatSubscriber#INITIAL_VALUE} upon creation</li>
         *     <li>Actual result if {@link #onSuccess(Object)} invoked before {@link #emitAdjustedItemIfAvailable()}
         *     </li>
         *     <li>{@link ConcatSubscriber#REQUESTED} if {@link #emitAdjustedItemIfAvailable()} invoked before
         *     {@link #onSuccess(Object)}</li>
         * </ul>
         */
        @Nullable
        private volatile Object mayBeResult = INITIAL_VALUE;
        private boolean adjustedRequestNForSingle;

        ConcatSubscriber(final Subscriber<? super T> subscriber, final Publisher<? extends T> next) {
            this.target = subscriber;
            this.next = next;
        }

        @Override
        public void onSubscribe(final Cancellable cancellable) {
            setCancellable(cancellable);
            target.onSubscribe(this);
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            Object oldValue = mayBeResultUpdater.getAndSet(this, result);
            if (oldValue == REQUESTED) {
                emitSingleSuccessToTarget(result);
            }
        }

        @Override
        public void onComplete() {
            target.onComplete();
        }

        @Override
        public void onSubscribe(final Subscription subscription) {
            setSubscription(subscription);
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
        boolean shouldAdjustRequestNBy1() {
            if (adjustedRequestNForSingle) {
                return false;
            }
            adjustedRequestNForSingle = true;
            return true;
        }

        @Override
        void emitAdjustedItemIfAvailable() {
            Object oldVal = mayBeResultUpdater.getAndSet(this, REQUESTED);
            if (oldVal != INITIAL_VALUE) {
                @SuppressWarnings("unchecked")
                T t = (T) oldVal;
                emitSingleSuccessToTarget(t);
            }
        }

        private void emitSingleSuccessToTarget(@Nullable final T result) {
            target.onNext(result);
            next.subscribeInternal(this);
        }
    }
}
