/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.context.api.ContextMap;

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;

import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionNonNormalReturn;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static io.servicetalk.utils.internal.ThrowableUtils.throwException;

/**
 * {@link Publisher} to do {@link Publisher#repeat(IntPredicate)} and {@link Publisher#retry(BiIntPredicate)}
 * operations.
 *
 * @param <T> Type of items emitted from this {@link Publisher}.
 */
final class RedoPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final boolean terminateOnNextException;
    private final Publisher<T> original;
    private final BiPredicate<Integer, TerminalNotification> shouldRedo;

    RedoPublisher(Publisher<T> original, boolean terminateOnNextException,
                  BiPredicate<Integer, TerminalNotification> shouldRedo) {
        this.original = original;
        this.terminateOnNextException = terminateOnNextException;
        this.shouldRedo = shouldRedo;
    }

    @Override
    void handleSubscribe(Subscriber<? super T> subscriber, ContextMap contextMap,
                         AsyncContextProvider contextProvider) {
        // For the current subscribe operation we want to use contextMap directly, but in the event a re-subscribe
        // operation occurs we want to restore the original state of the AsyncContext map, so we save a copy upfront.
        original.delegateSubscribe(new RedoSubscriber<>(terminateOnNextException, new SequentialSubscription(), 0,
                subscriber, contextMap.copy(), contextProvider, this), contextMap, contextProvider);
    }

    abstract static class AbstractRedoSubscriber<T> implements Subscriber<T> {
        /**
         * Unless you are sure all downstream operators consume the {@link Subscriber#onNext(Object)} this option
         * SHOULD be {@code true}. Otherwise, the outstanding demand counting in this operator will be incorrect and may
         * lead to a "hang" (e.g. this operator thinks demand has been consumed downstream so won't request it upstream
         * after the retry, but if not all downstream operators see the signal because one before threw, they may wait
         * for a signal they requested but will never be delivered).
         */
        private final boolean terminateOnNextException;
        private final SequentialSubscription subscription;
        private boolean terminated;
        final Subscriber<? super T> subscriber;
        int redoCount;

        AbstractRedoSubscriber(boolean terminateOnNextException, SequentialSubscription subscription, int redoCount,
                               Subscriber<? super T> subscriber) {
            this.terminateOnNextException = terminateOnNextException;
            this.subscription = subscription;
            this.redoCount = redoCount;
            this.subscriber = subscriber;
        }

        @Override
        public final void onSubscribe(Subscription s) {
            s = decorate(s);
            if (terminateOnNextException) {
                // ConcurrentSubscription because if exception is thrown from downstream onNext we invoke cancel which
                // may introduce concurrency on the subscription.
                s = ConcurrentSubscription.wrap(s);
            }
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

        @Override
        public final void onNext(T t) {
            if (terminated) {
                return;
            }
            subscription.itemReceived();
            try {
                subscriber.onNext(t);
            } catch (Throwable cause) {
                handleOnNextException(cause);
            }
        }

        @Override
        public final void onError(Throwable cause) {
            if (terminated) {
                return;
            }
            onError0(cause);
        }

        @Override
        public final void onComplete() {
            if (terminated) {
                return;
            }
            onComplete0();
        }

        abstract void onComplete0();

        abstract void onError0(Throwable cause);

        Subscription decorate(Subscription s) {
            return s;
        }

        private void handleOnNextException(Throwable cause) {
            cause = newExceptionNonNormalReturn(cause);
            if (!terminateOnNextException) {
                throwException(cause);
            } else if (terminated) { // just in case on-next delivered a terminal in re-entry fashion
                return;
            }
            terminated = true;
            try {
                subscription.cancel();
            } finally {
                subscriber.onError(cause);
            }
        }
    }

    private static final class RedoSubscriber<T> extends AbstractRedoSubscriber<T> {
        private final RedoPublisher<T> redoPublisher;
        private final ContextMap contextMap;
        private final AsyncContextProvider contextProvider;

        RedoSubscriber(boolean terminateOnNextException, SequentialSubscription subscription, int redoCount,
                       Subscriber<? super T> subscriber, ContextMap contextMap, AsyncContextProvider contextProvider,
                       RedoPublisher<T> redoPublisher) {
            super(terminateOnNextException, subscription, redoCount, subscriber);
            this.redoPublisher = redoPublisher;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
        }

        @Override
        void onError0(Throwable t) {
            tryRedo(TerminalNotification.error(t));
        }

        @Override
        void onComplete0() {
            tryRedo(complete());
        }

        private void tryRedo(TerminalNotification notification) {
            final boolean shouldRedo;
            try {
                shouldRedo = redoPublisher.shouldRedo.test(++redoCount, notification);
            } catch (Throwable cause) {
                Throwable originalCause = notification.cause();
                if (originalCause != null) {
                    addSuppressed(cause, originalCause);
                }
                subscriber.onError(cause);
                return;
            }

            if (shouldRedo) {
                // Either we copy the map up front before subscribe, or we just re-use the same map and let the async
                // source at the top of the chain reset if necessary. We currently choose the second option.
                redoPublisher.original.delegateSubscribe(this, contextMap, contextProvider);
            } else {
                notification.terminate(subscriber);
            }
        }
    }
}
