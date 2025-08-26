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
import io.servicetalk.concurrent.CompletableSource.Subscriber;

import java.util.function.BooleanSupplier;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;

/**
 * A set of factory methods that provides implementations for the various publish/subscribeOn methods on
 * {@link Completable}.
 *
 * <p>This implementation uses <i>task based</i> offloading. Signals are delivered on a thread owned by the provided
 * {@link Executor} invoked via the {@link Executor#execute(Runnable)} method independently for each signal.
 * No assumption should be made by applications that a consistent thread will be used for subsequent signals.
 */
final class PublishAndSubscribeOnCompletables {

    private PublishAndSubscribeOnCompletables() {
        // No instance.
    }

    static void deliverOnSubscribeAndOnError(Subscriber subscriber,
                                             CapturedContext capturedContext, AsyncContextProvider contextProvider,
                                             Throwable cause) {
        deliverErrorFromSource(contextProvider.wrapCompletableSubscriber(subscriber, capturedContext), cause);
    }

    static Completable publishOn(final Completable original,
                                 final BooleanSupplier shouldOffload,
                                 final io.servicetalk.concurrent.Executor executor) {
        return executor == immediate() ? original : new PublishOn(original, shouldOffload, executor);
    }

    static Completable subscribeOn(final Completable original,
                                   final BooleanSupplier shouldOffload,
                                   final io.servicetalk.concurrent.Executor executor) {
        return executor == immediate() ? original : new SubscribeOn(original, shouldOffload, executor);
    }

    /**
     * Completable that invokes the following methods on the provided executor:
     *
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     * </ul>
     */
    private static final class PublishOn extends TaskBasedAsyncCompletableOperator {

        PublishOn(final Completable original,
                  final BooleanSupplier shouldOffload,
                  final io.servicetalk.concurrent.Executor executor) {
            super(original, shouldOffload, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber,
                             final CapturedContext capturedContext, final AsyncContextProvider contextProvider) {
            final Subscriber upstreamSubscriber;
            try {
                BooleanSupplier shouldOffload = shouldOffload();
                upstreamSubscriber =
                        new CompletableSubscriberOffloadedTerminals(subscriber, shouldOffload, executor());

                // Note that the Executor is wrapped by default to preserve AsyncContext, so we don't have to re-wrap
                // the Subscriber.
            } catch (Throwable throwable) {
                deliverErrorFromSource(subscriber, throwable);
                return;
            }

            super.handleSubscribe(upstreamSubscriber, capturedContext, contextProvider);
        }
    }

    /**
     * Completable that invokes on the provided executor the following methods:
     *
     * <ul>
     *     <li>All {@link Cancellable} methods.</li>
     *     <li>The {@link #handleSubscribe(CompletableSource.Subscriber)} method.</li>
     * </ul>
     */
    private static final class SubscribeOn extends TaskBasedAsyncCompletableOperator {

        SubscribeOn(final Completable original,
                    final BooleanSupplier shouldOffload,
                    final io.servicetalk.concurrent.Executor executor) {
            super(original, shouldOffload, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber,
                             final CapturedContext capturedContext, final AsyncContextProvider contextProvider) {
            final Subscriber upstreamSubscriber;
            try {
                BooleanSupplier shouldOffload = shouldOffload();

                // The Executor preserves AsyncContext, so we don't have to re-wrap the Subscriber for async context
                // preservation, only offloading.
                upstreamSubscriber =
                        new CompletableSubscriberOffloadedCancellable(subscriber, shouldOffload, executor());

                if (shouldOffload.getAsBoolean()) {
                    // offload the remainder of subscribe()
                    executor().execute(() -> offloadedHandleSubscribe(
                            upstreamSubscriber, capturedContext, contextProvider));
                    return;
                }
            } catch (Throwable throwable) {
                // We assume that if executor accepted the task, it will be run otherwise handle thrown exception
                deliverErrorFromSource(subscriber, throwable);
                return;
            }

            // continue non-offloaded subscribe()
            super.handleSubscribe(upstreamSubscriber, capturedContext, contextProvider);
        }

        private void offloadedHandleSubscribe(final Subscriber upstreamSubscriber,
                                              final CapturedContext capturedContext,
                                              final AsyncContextProvider contextProvider) {
            // Because we run on a different thread, we must ensure any unexpected exception is caught.
            try {
                super.handleSubscribe(upstreamSubscriber, capturedContext, contextProvider);
            } catch (Throwable t) {
                LOGGER.warn("Unexpected exception from subscribe(), assuming no interaction with the Subscriber.", t);
                // At this point we are unsure if any signal was sent to the Subscriber and if it is safe to invoke the
                // Subscriber without violating specifications. However, not propagating the error to the Subscriber
                // will result in hard to debug scenarios where no further signals may be sent to the Subscriber and
                // hence it will be hard to distinguish between a "hung" source and a wrongly implemented source that
                // violates the specifications and throw from subscribe() (Rule 1.9).
                //
                // By doing the following we may violate the rules:
                // 1) Rule 2.12: onSubscribe() MUST be called at most once.
                // 2) Rule 1.7: Once a terminal state has been signaled (onError, onComplete) it is REQUIRED that no
                // further signals occur.
                deliverErrorFromSource(upstreamSubscriber, t);
            }
        }
    }
}
