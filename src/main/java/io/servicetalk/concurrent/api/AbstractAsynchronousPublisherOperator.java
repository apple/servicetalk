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

/**
 * Base class for operators on a {@link Publisher} that process signals asynchronously hence in order to guarantee safe
 * downstream invocations require to wrap their {@link Subscriber}s with an {@link InOrderExecutor}.
 * Operators that process signals synchronously can use {@link AbstractSynchronousPublisherOperator} to avoid wrapping
 * their {@link Subscriber}s and hence reduce object allocation.
 *
 * @param <T> Type of original {@link Publisher}.
 * @param <R> Type of {@link Publisher} returned by the operator.
 *
 * @see AbstractSynchronousPublisherOperator
 */
abstract class AbstractAsynchronousPublisherOperator<T, R> extends FailRegularSubscribePublisher<R>
        implements PublisherOperator<T, R> {

    private final Publisher<T> original;

    AbstractAsynchronousPublisherOperator(Publisher<T> original, Executor executor) {
        super(executor);
        this.original = requireNonNull(original);
    }

    @Override
    final void handleSubscribe(Subscriber<? super R> subscriber, InOrderExecutor inOrderExecutor) {
        // Wrap the passed Subscriber with the InOrderExecutor to make sure they are not invoked in the thread that
        // asynchronously processes signals and hence may not be safe to execute user code.
        original.subscribe(apply(requireNonNull(inOrderExecutor.wrap(subscriber))), inOrderExecutor);
    }
}
