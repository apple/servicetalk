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

import org.reactivestreams.Subscriber;

import static java.util.Objects.requireNonNull;

abstract class AbstractRedoPublisherOperator<T> extends AbstractNoHandleSubscribePublisher<T> {

    private final Publisher<T> original;

    AbstractRedoPublisherOperator(Publisher<T> original, Executor executor) {
        super(executor);
        this.original = requireNonNull(original);
    }

    @Override
    final void handleSubscribe(Subscriber<? super T> subscriber, InOrderExecutor inOrderExecutor) {
        subscribeToOriginal(requireNonNull(redo(subscriber, inOrderExecutor)), inOrderExecutor);
    }

    /**
     * Subscribes the passed {@link Subscriber} to the original {@link Publisher}.
     *
     * @param subscriber {@link Subscriber} to use.
     * @param inOrderExecutor {@link InOrderExecutor} for the passed {@link Subscriber}.
     */
    final void subscribeToOriginal(Subscriber<? super T> subscriber, InOrderExecutor inOrderExecutor) {
        original.subscribe(subscriber, inOrderExecutor);
    }

    /**
     * Bridges this {@link Publisher}'s {@link Subscriber} to the original {@link Publisher}'s {@link Subscriber}.
     *
     * @param subscriber {@link Subscriber} to this {@link Publisher}.
     * @param inOrderExecutor  {@link InOrderExecutor} for the passed {@link Subscriber}. Typically, implementations will
     *                         not use this {@link InOrderExecutor} but just pass it to {@link #subscribeToOriginal(Subscriber, InOrderExecutor)}
     *                         at a later point.
     * @return {@link Subscriber} to subscribe to the original {@link Subscriber}.
     */
    abstract Subscriber<? super T> redo(Subscriber<? super T> subscriber, InOrderExecutor inOrderExecutor);
}
