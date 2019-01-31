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
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.concurrent.internal.SignalOffloader;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.function.BiFunction;
import java.util.function.IntFunction;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static java.util.Objects.requireNonNull;

/**
 * {@link Publisher} to do {@link Publisher#repeatWhen(IntFunction)} and {@link Publisher#retryWhen(BiIntFunction)}
 * operations.
 *
 * @param <T> Type of items emitted from this {@link Publisher}.
 */
final class RedoWhenPublisher<T> extends AbstractNoHandleSubscribePublisher<T> {

    private final Publisher<T> original;
    private final BiFunction<Integer, TerminalNotification, Completable> shouldRedo;
    private final boolean forRetry;

    /**
     * New instance.
     *
     * @param original {@link Publisher} on which this operator is applied.
     * @param shouldRedo {@link BiFunction} to create a {@link Completable} that determines whether to redo the
     * operation.
     * @param forRetry If redo has to be done for error i.e. it is used for retry. If {@code true} completion for
     * original source will complete the subscriber. Otherwise, error will send the error to the subscriber.
     * @param executor {@link Executor} for this {@link Publisher}.
     */
    RedoWhenPublisher(Publisher<T> original, BiFunction<Integer, TerminalNotification, Completable> shouldRedo,
                      boolean forRetry, Executor executor) {
        super(executor);
        this.original = original;
        this.shouldRedo = shouldRedo;
        this.forRetry = forRetry;
    }

    @Override
    void handleSubscribe(Subscriber<? super T> subscriber, SignalOffloader signalOffloader,
                         AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        // For the current subscribe operation we want to use contextMap directly, but in the event a re-subscribe
        // operation occurs we want to restore the original state of the AsyncContext map, so we save a copy upfront.
        original.subscribeWithOffloaderAndContext(new RedoSubscriber<>(new SequentialSubscription(), 0, subscriber,
                contextMap.copy(), contextProvider, this), signalOffloader, contextMap, contextProvider);
    }

    private static final class RedoSubscriber<T> extends RedoPublisher.AbstractRedoSubscriber<T> {

        private final SequentialCancellable cancellable;
        private final RedoWhenPublisher<T> redoPublisher;
        private final AsyncContextMap contextMap;
        private final AsyncContextProvider contextProvider;

        RedoSubscriber(SequentialSubscription subscription, int redoCount, Subscriber<? super T> subscriber,
                       AsyncContextMap contextMap, AsyncContextProvider contextProvider,
                       RedoWhenPublisher<T> redoPublisher) {
            super(subscription, redoCount, subscriber);
            this.redoPublisher = redoPublisher;
            this.contextMap = contextMap;
            this.contextProvider = contextProvider;
            cancellable = new SequentialCancellable();
        }

        @Override
        public void onNext(T t) {
            subscription.itemReceived();
            subscriber.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            if (!redoPublisher.forRetry) {
                subscriber.onError(t);
                return;
            }

            TerminalNotification terminalNotification = TerminalNotification.error(t);
            redoIfRequired(terminalNotification);
        }

        @Override
        public void onComplete() {
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
                redoDecider = requireNonNull(redoPublisher.shouldRedo.apply(redoCount + 1, terminalNotification));
            } catch (Throwable cause) {
                Throwable originalCause = terminalNotification.getCause();
                if (originalCause != null) {
                    cause.addSuppressed(originalCause);
                }
                subscriber.onError(cause);
                return;
            }

            redoDecider.subscribe(new Completable.Subscriber() {
                @Override
                public void onSubscribe(Cancellable completableCancellable) {
                    cancellable.setNextCancellable(completableCancellable);
                }

                @Override
                public void onComplete() {
                    // For the current subscribe operation we want to use contextMap directly, but in the event a
                    // re-subscribe operation occurs we want to restore the original state of the AsyncContext map, so
                    // we save a copy upfront.
                    redoPublisher.original.subscribeWithContext(new RedoSubscriber<>(subscription, redoCount + 1,
                        subscriber, contextMap.copy(), contextProvider, redoPublisher), contextMap,
                            contextProvider);
                }

                @Override
                public void onError(Throwable t) {
                    if (!redoPublisher.forRetry) {
                        // repeat operator terminates repeat with error.
                        subscriber.onComplete();
                    } else {
                        subscriber.onError(t);
                    }
                }
            });
        }
    }
}
