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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.RedoPublisher.AbstractRedoSubscriber;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.concurrent.internal.TerminalNotification;
import io.servicetalk.context.api.ContextMap;

import java.util.function.BiFunction;
import java.util.function.IntFunction;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.util.Objects.requireNonNull;

/**
 * {@link Publisher} to do {@link Publisher#repeatWhen(IntFunction)} and {@link Publisher#retryWhen(BiIntFunction)}
 * operations.
 *
 * @param <T> Type of items emitted from this {@link Publisher}.
 */
final class RedoWhenPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {
    private final boolean terminateOnNextException;
    private final Publisher<T> original;
    private final BiFunction<Integer, TerminalNotification, Completable> shouldRedo;
    private final boolean forRetry;

    /**
     * New instance.
     *
     * @param original {@link Publisher} on which this operator is applied.
     * @param forRetry If redo has to be done for error i.e. it is used for retry. If {@code true} completion for
     * original source will complete the subscriber. Otherwise, error will send the error to the subscriber.
     * @param terminateOnNextException {@code true} exceptions from {@link Subscriber#onNext(Object)} will be caught and
     * terminated inside this operator (and the {@link Subscription} will be cancelled). {@code false} means exceptions
     * from {@link Subscriber#onNext(Object)} will not be caught.
     * @param shouldRedo {@link BiFunction} to create a {@link Completable} that determines whether to redo the
     * operation.
     */
    RedoWhenPublisher(Publisher<T> original, boolean forRetry, boolean terminateOnNextException,
                      BiFunction<Integer, TerminalNotification, Completable> shouldRedo) {
        this.original = original;
        this.forRetry = forRetry;
        this.terminateOnNextException = terminateOnNextException;
        this.shouldRedo = shouldRedo;
    }

    @Override
    void handleSubscribe(Subscriber<? super T> subscriber,
                         ContextMap contextMap, AsyncContextProvider contextProvider) {
        // For the current subscribe operation we want to use contextMap directly, but in the event a re-subscribe
        // operation occurs we want to restore the original state of the AsyncContext map.
        original.delegateSubscribe(new RedoSubscriber<>(terminateOnNextException, new SequentialSubscription(), 0,
                        subscriber, contextMap, contextProvider, this), contextMap, contextProvider);
    }

    private static final class RedoSubscriber<T> extends AbstractRedoSubscriber<T> {
        private final SequentialCancellable cancellable;
        private final RedoWhenPublisher<T> redoPublisher;
        private final ContextMap contextMap;
        private final AsyncContextProvider contextProvider;
        private final CompletableSource.Subscriber completableSubscriber = new CompletableSource.Subscriber() {
            @Override
            public void onSubscribe(Cancellable completableCancellable) {
                cancellable.nextCancellable(completableCancellable);
            }

            @Override
            public void onComplete() {
                // Either we copy the map up front before subscribe, or we just re-use the same map and let the async
                // source at the top of the chain reset if necessary. We currently choose the second option.
                redoPublisher.original.delegateSubscribe(RedoSubscriber.this, contextMap, contextProvider);
            }

            @Override
            public void onError(Throwable t) {
                // No need to check if terminated already because this Completable is only subscribed
                // if a terminal is received from upstream but not yet delivered downstream.
                if (!redoPublisher.forRetry) {
                    // repeat operator terminates repeat with error.
                    subscriber.onComplete();
                } else {
                    subscriber.onError(t);
                }
            }
        };

        RedoSubscriber(boolean terminateOnNextException, SequentialSubscription subscription, int redoCount,
                       Subscriber<? super T> subscriber, ContextMap contextMap, AsyncContextProvider contextProvider,
                       RedoWhenPublisher<T> redoPublisher) {
            super(terminateOnNextException, subscription, redoCount, subscriber);
            this.redoPublisher = redoPublisher;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
            cancellable = new SequentialCancellable();
        }

        @Override
        void onError0(Throwable t) {
            if (!redoPublisher.forRetry) {
                subscriber.onError(t);
                return;
            }

            TerminalNotification terminalNotification = TerminalNotification.error(t);
            redoIfRequired(terminalNotification);
        }

        @Override
        void onComplete0() {
            if (redoPublisher.forRetry) {
                subscriber.onComplete();
                return;
            }
            redoIfRequired(complete());
        }

        @Override
        Subscription decorate(Subscription s) {
            return new MergedCancellableWithSubscription(s, cancellable);
        }

        private void redoIfRequired(TerminalNotification terminalNotification) {
            final Completable redoDecider;
            try {
                redoDecider = requireNonNull(redoPublisher.shouldRedo.apply(++redoCount, terminalNotification),
                        () -> "Redo decider " + redoPublisher.shouldRedo + " returned null");
            } catch (Throwable cause) {
                Throwable originalCause = terminalNotification.cause();
                if (originalCause != null) {
                    addSuppressed(cause, originalCause);
                }
                subscriber.onError(cause);
                return;
            }

            redoDecider.subscribeInternal(completableSubscriber);
        }
    }
}
