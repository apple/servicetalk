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

import io.servicetalk.concurrent.internal.SignalOffloader;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;

/**
 * {@link Publisher} to do {@link Publisher#repeat(IntPredicate)} and {@link Publisher#retry(BiIntPredicate)}
 * operations.
 *
 * @param <T> Type of items emitted from this {@link Publisher}.
 */
final class RedoPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {

    private final Publisher<T> original;
    private final BiPredicate<Integer, TerminalNotification> shouldRedo;

    RedoPublisher(Publisher<T> original, BiPredicate<Integer, TerminalNotification> shouldRedo) {
        this.original = original;
        this.shouldRedo = shouldRedo;
    }

    @Override
    Executor executor() {
        return original.executor();
    }

    @Override
    void handleSubscribe(Subscriber<? super T> subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
                         AsyncContextProvider contextProvider) {
        // For the current subscribe operation we want to use contextMap directly, but in the event a re-subscribe
        // operation occurs we want to restore the original state of the AsyncContext map, so we save a copy upfront.
        original.delegateSubscribe(new RedoSubscriber<>(new SequentialSubscription(), 0, subscriber, contextMap.copy(),
                contextProvider, this, signalOffloader), signalOffloader, contextMap, contextProvider);
    }

    abstract static class AbstractRedoSubscriber<T> implements Subscriber<T> {

        final SequentialSubscription subscription;
        final int redoCount;
        final Subscriber<? super T> subscriber;

        AbstractRedoSubscriber(SequentialSubscription subscription, int redoCount, Subscriber<? super T> subscriber) {
            this.subscription = subscription;
            this.redoCount = redoCount;
            this.subscriber = subscriber;
        }

        @Override
        public final void onSubscribe(Subscription s) {
            s = decorate(s);
            // Downstream Subscriber only gets one Subscription but every time we re-subscribe we switch the current
            // Subscription in SequentialSubscription to the new Subscription. This will make sure that we always
            // request from the "current" Subscription.
            // Concurrent access: Since, downstream from here sees only one Subscription, there would be no concurrent
            // access to it. SequentialSubscription is responsible for managing the concurrency between request-n,
            // switch, and itemReceived.
            subscription.switchTo(s);
            if (redoCount == 0) {
                subscriber.onSubscribe(subscription);
            }
        }

        Subscription decorate(Subscription s) {
            return s;
        }
    }

    private static final class RedoSubscriber<T> extends AbstractRedoSubscriber<T> {

        private final RedoPublisher<T> redoPublisher;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        private final SignalOffloader offloader;

        RedoSubscriber(SequentialSubscription subscription, int redoCount, Subscriber<? super T> subscriber,
                       AsyncContextMap contextMap, AsyncContextProvider contextProvider,
                       RedoPublisher<T> redoPublisher, SignalOffloader offloader) {
            super(subscription, redoCount, subscriber);
            this.redoPublisher = redoPublisher;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
            this.offloader = offloader;
        }

        @Override
        public void onNext(T t) {
            subscription.itemReceived();
            subscriber.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            tryRedo(TerminalNotification.error(t));
        }

        @Override
        public void onComplete() {
            tryRedo(complete());
        }

        private void tryRedo(TerminalNotification notification) {
            final boolean shouldRedo;
            try {
                shouldRedo = redoPublisher.shouldRedo.test(redoCount + 1, notification);
            } catch (Throwable cause) {
                Throwable originalCause = notification.cause();
                if (originalCause != null) {
                    cause.addSuppressed(originalCause);
                }
                subscriber.onError(cause);
                return;
            }

            if (shouldRedo) {
                // For the current subscribe operation we want to use contextMap directly, but in the event a
                // re-subscribe operation occurs we want to restore the original state of the AsyncContext map, so
                // we save a copy upfront.
                redoPublisher.original.delegateSubscribe(
                        new RedoSubscriber<>(subscription, redoCount + 1,
                        subscriber, contextMap.copy(), contextProvider, redoPublisher, offloader), offloader,
                        contextMap, contextProvider);
            } else {
                notification.terminate(subscriber);
            }
        }
    }
}
