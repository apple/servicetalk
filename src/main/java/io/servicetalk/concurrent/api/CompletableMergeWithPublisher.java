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
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.concurrent.internal.DelayedSubscription;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.checkTerminationValidWithConcurrentOnNextCheck;
import static io.servicetalk.concurrent.internal.SubscriberUtils.sendOnNextWithConcurrentTerminationCheck;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;

/**
 * {@link Publisher} as returned by {@link Completable#merge(Publisher)}.
 *
 * @param <T> Type of data returned from the {@link Publisher}
 */
final class CompletableMergeWithPublisher<T> extends Publisher<T> {

    private final Completable original;
    private final Publisher<T> mergeWith;

    CompletableMergeWithPublisher(Completable original, Publisher<T> mergeWith) {
        this.mergeWith = mergeWith;
        this.original = original;
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber) {
        new Merger<>(subscriber).merge(original, mergeWith);
    }

    private static final class Merger<T> implements Subscriber<T> {

        private static final AtomicIntegerFieldUpdater<Merger> completionCountUpdater =
                AtomicIntegerFieldUpdater.newUpdater(Merger.class, "completionCount");
        private static final AtomicIntegerFieldUpdater<Merger> subscriberStateUpdater =
                AtomicIntegerFieldUpdater.newUpdater(Merger.class, "subscriberState");
        private static final AtomicReferenceFieldUpdater<Merger, Object> terminalNotificationUpdater =
                AtomicReferenceFieldUpdater.newUpdater(Merger.class, Object.class, "terminalNotification");

        @SuppressWarnings("unused")
        private volatile int completionCount;
        @SuppressWarnings("unused")
        private volatile int subscriberState;
        @Nullable
        @SuppressWarnings("unused")
        private volatile Object terminalNotification;

        private final Subscriber<? super T> subscriber;
        private final CompletableSubscriber completableSubscriber = new CompletableSubscriber();
        private final DelayedSubscription subscription = new DelayedSubscription();

        private Merger(Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        private void merge(Completable original, Publisher<? extends T> mergeWith) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    subscription.request(n);
                }

                @Override
                public void cancel() {
                    subscription.cancel();
                    completableSubscriber.cancel();
                }
            });
            original.subscribe(completableSubscriber);
            mergeWith.subscribe(this);
        }

        private void onError0(Throwable t) {
            if (checkTerminationValidWithConcurrentOnNextCheck(null, t,
                    subscriberStateUpdater, terminalNotificationUpdater, this)) {
                subscriber.onError(t);
            }
        }

        private void onComplete0() {
            if (completionCountUpdater.incrementAndGet(this) == 2 &&
                    checkTerminationValidWithConcurrentOnNextCheck(null, complete(),
                            subscriberStateUpdater, terminalNotificationUpdater, this)) {
                subscriber.onComplete();
            }
        }

        @Override
        public void onSubscribe(Subscription targetSubscription) {
            subscription.setDelayedSubscription(targetSubscription);
        }

        @Override
        public void onNext(T t) {
            sendOnNextWithConcurrentTerminationCheck(() -> subscriber.onNext(t), this::onTerminatedConcurrently,
                    subscriberStateUpdater, terminalNotificationUpdater, this);
        }

        @Override
        public void onError(Throwable t) {
            completableSubscriber.cancel();
            onError0(t);
        }

        @Override
        public void onComplete() {
            onComplete0();
        }

        /**
         * Concurrency in this operator wrt the downstream {@link Subscriber} originates from the merged {@link
         * Completable} when it terminates with an error and needs to cancel the {@link Publisher} to stop emitting.
         * There is no alternative concurrent path where the {@link Publisher} {@link Subscriber} can be concurrently
         * terminated due to the {@link #completionCount}, so this method should only ever be called with a {@link
         * Throwable} argument.
         *
         * @param terminalNotification exception thrown from the {@link Completable}
         */
        private void onTerminatedConcurrently(Object terminalNotification) {
            assert terminalNotification instanceof Throwable : "Should never concurrently complete";
            subscriber.onError((Throwable) terminalNotification);
        }

        private final class CompletableSubscriber extends DelayedCancellable implements Completable.Subscriber {

            @Override
            public void onSubscribe(Cancellable cancellable) {
                setDelayedCancellable(cancellable);
            }

            @Override
            public void onComplete() {
                onComplete0();
            }

            @Override
            public void onError(Throwable t) {
                subscription.cancel();
                onError0(t);
            }
        }
    }
}
