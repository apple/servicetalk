/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.SignalOffloader;

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
final class CompletableMergeWithPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {

    private final Completable original;
    private final Publisher<T> mergeWith;

    CompletableMergeWithPublisher(Completable original, Publisher<T> mergeWith, Executor executor) {
        super(executor);
        this.mergeWith = mergeWith;
        this.original = original;
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber, final SignalOffloader signalOffloader,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        new Merger<>(subscriber, signalOffloader, contextMap, contextProvider)
                .merge(original, mergeWith, signalOffloader, contextMap, contextProvider);
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

        private final CompletableSubscriber completableSubscriber;
        private final Subscriber<? super T> offloadedSubscriber;
        private final DelayedSubscription subscription = new DelayedSubscription();

        Merger(Subscriber<? super T> subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
               AsyncContextProvider contextProvider) {
            // The CompletableSubscriber and offloadedSubscriber may interact with the subscriber, so we need to wrap it
            // and make sure the expected context is restored.
            subscriber = contextProvider.wrap(subscriber, contextMap);

            completableSubscriber = new CompletableSubscriber(subscriber);
            // This is used only to deliver signals that originate from the mergeWith Publisher. Since, we need to
            // preserve the threading semantics of the original Completable, we offload the subscriber so that we do not
            // invoke it from the mergeWith Publisher Executor thread.
            this.offloadedSubscriber = signalOffloader.offloadSubscriber(subscriber);
        }

        void merge(Completable original, Publisher<? extends T> mergeWith, SignalOffloader signalOffloader,
                   AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
            offloadedSubscriber.onSubscribe(new MergedCancellableWithSubscription(subscription, completableSubscriber));
            original.subscribeWithOffloaderAndContext(completableSubscriber, signalOffloader, contextMap,
                    contextProvider);
            // SignalOffloader is associated with the original Completable. Since mergeWith Publisher is provided by
            // the user, it will have its own Executor, hence we should not pass this signalOffloader to subscribe to
            // mergeWith.
            // Any signal originating from mergeWith Publisher should be offloaded before they are sent to the
            // Subscriber of the resulting Publisher of CompletableMergeWithPublisher as the Executor associated with
            // the original Completable defines the threading semantics for that Subscriber.
            mergeWith.subscribeWithContext(this, contextMap.copy(), contextProvider);
        }

        @Override
        public void onSubscribe(Subscription targetSubscription) {
            subscription.delayedSubscription(targetSubscription);
        }

        @Override
        public void onNext(T t) {
            sendOnNextWithConcurrentTerminationCheck(offloadedSubscriber, t, this::onTerminatedConcurrently,
                    subscriberStateUpdater, terminalNotificationUpdater, this);
        }

        @Override
        public void onError(Throwable t) {
            completableSubscriber.cancel();
            if (checkTerminationValidWithConcurrentOnNextCheck(null, t,
                    subscriberStateUpdater, terminalNotificationUpdater, this)) {
                offloadedSubscriber.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (completionCountUpdater.incrementAndGet(this) == 2 &&
                    checkTerminationValidWithConcurrentOnNextCheck(null, complete(),
                            subscriberStateUpdater, terminalNotificationUpdater, this)) {
                offloadedSubscriber.onComplete();
            }
        }

        /**
         * Concurrency in this operator wrt the downstream {@link Subscriber} originates from the merged {@link
         * Completable} when it terminates with an error and needs to terminate the {@link Publisher}.
         * There is no alternative concurrent path where the {@link Publisher} {@link Subscriber} can be concurrently
         * terminated due to the {@link #completionCount}, so this method should only ever be called with a {@link
         * Throwable} argument.
         *
         * @param terminalNotification exception thrown from the {@link Completable}
         */
        private void onTerminatedConcurrently(Object terminalNotification) {
            assert terminalNotification instanceof Throwable : "Should never concurrently complete";
            offloadedSubscriber.onError((Throwable) terminalNotification);
        }

        private final class CompletableSubscriber extends DelayedCancellable implements Completable.Subscriber {

            private final Subscriber<? super T> subscriber;

            CompletableSubscriber(final Subscriber<? super T> subscriber) {
                this.subscriber = subscriber;
            }

            @Override
            public void onSubscribe(Cancellable cancellable) {
                delayedCancellable(cancellable);
            }

            @Override
            public void onComplete() {
                if (completionCountUpdater.incrementAndGet(Merger.this) == 2 &&
                        checkTerminationValidWithConcurrentOnNextCheck(null, complete(),
                                subscriberStateUpdater, terminalNotificationUpdater, Merger.this)) {
                    subscriber.onComplete();
                }
            }

            @Override
            public void onError(Throwable t) {
                subscription.cancel();
                if (checkTerminationValidWithConcurrentOnNextCheck(null, t,
                        subscriberStateUpdater, terminalNotificationUpdater, Merger.this)) {
                    subscriber.onError(t);
                }
            }
        }
    }
}
