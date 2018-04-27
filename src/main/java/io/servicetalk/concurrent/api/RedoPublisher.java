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

import io.servicetalk.concurrent.internal.TerminalNotification;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;

/**
 * {@link Publisher} to do {@link Publisher#repeat(IntPredicate)} and {@link Publisher#retry(BiIntPredicate)} operations.
 *
 * @param <T> Type of items emitted from this {@link Publisher}.
 */
final class RedoPublisher<T> extends AbstractRedoPublisherOperator<T> {

    private final BiPredicate<Integer, TerminalNotification> shouldRedo;

    RedoPublisher(Publisher<T> original, BiPredicate<Integer, TerminalNotification> shouldRedo, Executor executor) {
        super(original, executor);
        this.shouldRedo = shouldRedo;
    }

    @Override
    Subscriber<? super T> redo(Subscriber<? super T> subscriber, SignalOffloader signalOffloader) {
        return new RedoSubscriber<>(new SequentialSubscription(), 0, subscriber, this, signalOffloader);
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
            // Downstream Subscriber only gets one Subscription but every time we re-subscribe we switch the current Subscription
            // in SequentialSubscription to the new Subscription. This will make sure that we always request from the "current"
            // Subscription.
            // Concurrent access: Since, downstream from here sees only one Subscription, there would be no concurrent access to it.
            // SequentialSubscription makes sure that request-n, switch and itemReceived are atomic and do not lose
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
        private final SignalOffloader signalOffloader;

        RedoSubscriber(SequentialSubscription subscription, int redoCount, Subscriber<? super T> subscriber,
                       RedoPublisher<T> redoPublisher, SignalOffloader signalOffloader) {
            super(subscription, redoCount, subscriber);
            this.redoPublisher = redoPublisher;
            this.signalOffloader = signalOffloader;
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
                Throwable originalCause = notification.getCause();
                if (originalCause != null) {
                    cause.addSuppressed(originalCause);
                }
                subscriber.onError(cause);
                return;
            }

            if (shouldRedo) {
                redoPublisher.subscribeToOriginal(new RedoSubscriber<>(subscription, redoCount + 1, subscriber, redoPublisher, signalOffloader), signalOffloader);
            } else {
                notification.terminate(subscriber);
            }
        }
    }
}
