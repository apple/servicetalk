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
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;

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
                                             AsyncContextMap contextMap, AsyncContextProvider contextProvider,
                                             Throwable cause) {
        deliverErrorFromSource(contextProvider.wrapCompletableSubscriber(subscriber, contextMap), cause);
    }

    @FunctionalInterface
    private interface HandleSubscribe {
        void handleSubscribe(Subscriber subscriber, AsyncContextMap contextMap, AsyncContextProvider contextProvider);
    }

    private static void safeHandleSubscribe(final HandleSubscribe handler, final Subscriber subscriber,
                                    final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        try {
            handler.handleSubscribe(subscriber, contextMap, contextProvider);
        } catch (Throwable throwable) {
            safeOnError(subscriber, throwable);
        }
    }

    static Completable publishOn(final Completable original,
                                 final BooleanSupplier shouldOffload, final Executor executor) {
        return executor == immediate() ? original : new PublishOn(original, shouldOffload, executor);
    }

    static Completable subscribeOn(final Completable original,
                                   final BooleanSupplier shouldOffload, final Executor executor) {
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
                  final BooleanSupplier shouldOffload, final Executor executor) {
            super(original, shouldOffload, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
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

            safeHandleSubscribe(super::handleSubscribe, upstreamSubscriber, contextMap, contextProvider);
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
                    final BooleanSupplier shouldOffload, final Executor executor) {
            super(original, shouldOffload, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            final Subscriber upstreamSubscriber;
            try {
                BooleanSupplier shouldOffload = shouldOffload();

                // The Executor preserves AsyncContext, so we don't have to re-wrap the Subscriber for async context
                // preservation, only offloading.
                upstreamSubscriber =
                        new CompletableSubscriberOffloadedCancellable(subscriber, shouldOffload, executor());

                if (shouldOffload.getAsBoolean()) {
                    // offload the remainder of subscribe()
                    executor().execute(() -> safeHandleSubscribe(super::handleSubscribe,
                            upstreamSubscriber, contextMap, contextProvider));
                    return;
                }
            } catch (Throwable throwable) {
                // We assume that if executor accepted the task, it will be run otherwise handle thrown exception
                deliverErrorFromSource(subscriber, throwable);
                return;
            }

            // continue non-offloaded subscribe()
            safeHandleSubscribe(super::handleSubscribe, upstreamSubscriber, contextMap, contextProvider);
        }
    }
}
