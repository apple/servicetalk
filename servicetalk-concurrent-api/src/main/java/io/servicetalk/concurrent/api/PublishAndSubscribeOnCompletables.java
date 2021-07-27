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
import java.util.function.Supplier;

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
                                             AsyncContextMap contextMap, AsyncContextProvider contextProvider,
                                             Throwable cause) {
        deliverErrorFromSource(contextProvider.wrapCompletableSubscriber(subscriber, contextMap), cause);
    }

    static Completable publishOn(final Completable original,
                                 final Supplier<? extends BooleanSupplier> shouldOffload, final Executor executor) {
        return executor == immediate() ? original : new PublishOn(original, shouldOffload, executor);
    }

    static Completable subscribeOn(final Completable original,
                                   final Supplier<? extends BooleanSupplier> shouldOffload, final Executor executor) {
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
                  final Supplier<? extends BooleanSupplier> shouldOffloadSupplier, final Executor executor) {
            super(original, shouldOffloadSupplier, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            // re-wrap the subscriber so that async context is restored during offloading.
            Subscriber wrapped = contextProvider.wrapCompletableSubscriber(subscriber, contextMap);

            BooleanSupplier shouldOffload = shouldOffload();
            Subscriber upstreamSubscriber =
                    new CompletableSubscriberOffloadedTerminals(wrapped, shouldOffload, executor());

            super.handleSubscribe(upstreamSubscriber, contextMap, contextProvider);
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
                    final Supplier<? extends BooleanSupplier> shouldOffloadSupplier, final Executor executor) {
            super(original, shouldOffloadSupplier, executor);
        }

        @Override
        void handleSubscribe(final Subscriber subscriber,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            try {
                // re-wrap the subscriber so that async context is restored during offloading.
                Subscriber wrapped = contextProvider.wrapCancellable(subscriber, contextMap);

                BooleanSupplier shouldOffload = shouldOffload();
                Subscriber upstreamSubscriber =
                        new CompletableSubscriberOffloadedCancellable(wrapped, shouldOffload, executor());

                // offload the remainder of subscribe()
                if (shouldOffload.getAsBoolean()) {
                    executor().execute(contextProvider.wrapRunnable(() ->
                            super.handleSubscribe(upstreamSubscriber, contextMap, contextProvider), contextMap));
                } else {
                    super.handleSubscribe(upstreamSubscriber, contextMap, contextProvider);
                }
            } catch (Throwable throwable) {
                // We assume that if executor accepted the task, it will be run otherwise handle thrown exception
                deliverErrorFromSource(subscriber, throwable);
            }
        }
    }
}
