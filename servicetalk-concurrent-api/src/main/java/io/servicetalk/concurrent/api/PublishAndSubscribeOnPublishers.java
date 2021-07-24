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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;

/**
 * A set of factory methods that provides implementations for the various publish/subscribeOn methods on
 * {@link Publisher}.
 */
final class PublishAndSubscribeOnPublishers {

    private PublishAndSubscribeOnPublishers() {
        // No instance.
    }

    static <T> void deliverOnSubscribeAndOnError(Subscriber<? super T> subscriber,
                                                 AsyncContextMap contextMap, AsyncContextProvider contextProvider,
                                                 Throwable cause) {
        deliverErrorFromSource(contextProvider.wrapPublisherSubscriber(subscriber, contextMap), cause);
    }

    static <T> Publisher<T> publishOn(final Publisher<T> original,
                                      final Supplier<? extends BooleanSupplier> shouldOffload,
                                      final Executor executor) {
        return immediate() == executor ? original : new PublishOn<>(original, shouldOffload, executor);
    }

    static <T> Publisher<T> subscribeOn(final Publisher<T> original,
                                        final Supplier<? extends BooleanSupplier> shouldOffload,
                                        final Executor executor) {
        return immediate() == executor ? original : new SubscribeOn<>(original, shouldOffload, executor);
    }

    /**
     * {@link Publisher} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Subscriber} methods.</li>
     * </ul>
     * @param <T> type of items
     */
    private static final class PublishOn<T> extends TaskBasedAsyncPublisherOperator<T> {

        PublishOn(final Publisher<T> original,
                  final Supplier<? extends BooleanSupplier> shouldOffloadSupplier, final Executor executor) {
            super(original, shouldOffloadSupplier, executor);
        }

        @Override
        void handleSubscribe(final Subscriber<? super T> subscriber,
                             final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            // re-wrap the subscriber so that async context is restored during offloading.
            Subscriber<? super T> wrapped = contextProvider.wrapPublisherSubscriber(subscriber, contextMap);

            BooleanSupplier shouldOffload = shouldOffload();
            final Subscriber<? super T> upstreamSubscriber =
                    new OffloadedSubscriber<>(wrapped, shouldOffload, executor());

            super.handleSubscribe(upstreamSubscriber, contextMap, contextProvider);
        }
    }

    /**
     * {@link Publisher} that will use the passed {@link Executor} to invoke the following methods:
     * <ul>
     *     <li>All {@link Subscription} methods.</li>
     *     <li>The {@link #handleSubscribe(PublisherSource.Subscriber)} method.</li>
     * </ul>
     *
     * @param <T> type of items
     */
    private static final class SubscribeOn<T> extends TaskBasedAsyncPublisherOperator<T> {

        SubscribeOn(final Publisher<T> original,
                    final Supplier<? extends BooleanSupplier> shouldOffloadSupplier, final Executor executor) {
            super(original, shouldOffloadSupplier, executor);
        }

        @Override
        public void handleSubscribe(final Subscriber<? super T> subscriber,
                                    final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
            try {
                // re-wrap the subscriber so that async context is restored during offloading.
                Subscriber<? super T> wrapped = contextProvider.wrapSubscription(subscriber, contextMap);

                BooleanSupplier shouldOffload = shouldOffload();
                final Subscriber<? super T> upstreamSubscriber =
                        new OffloadedSubscriptionSubscriber<>(wrapped, shouldOffload, executor());

                // offload the remainder of subscribe()
                if (shouldOffload.getAsBoolean()) {
                    executor().execute(
                            () -> super.handleSubscribe(upstreamSubscriber, contextMap, contextProvider));
                } else {
                    super.handleSubscribe(upstreamSubscriber, contextMap, contextProvider);
                }
            } catch (Throwable throwable) {
                // We assume that if executor accepted the task, it will be run otherwise handle thrown exception
                // note that the subscriber error is not offloaded.
                deliverErrorFromSource(subscriber, throwable);
            }
        }
    }
}
