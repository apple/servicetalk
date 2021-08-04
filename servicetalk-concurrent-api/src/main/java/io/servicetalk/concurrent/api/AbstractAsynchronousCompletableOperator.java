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

import static java.util.Objects.requireNonNull;

/**
 * Base class for operators on a {@link Completable} that process signals asynchronously hence in order to guarantee
 * safe downstream invocations require to wrap their {@link Subscriber}s with offloading.
 * Operators that process signals synchronously can use {@link AbstractSynchronousCompletableOperator} to avoid wrapping
 * their {@link Subscriber}s and hence reduce object allocation.
 *
 * @see AbstractSynchronousCompletableOperator
 */
abstract class AbstractAsynchronousCompletableOperator extends AbstractNoHandleSubscribeCompletable
        implements CompletableOperator {

    final Completable original;

    AbstractAsynchronousCompletableOperator(Completable original) {
        this.original = requireNonNull(original);
    }

    @Override
    final void handleSubscribe(Subscriber subscriber,
                               AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        // The AsyncContext needs to be preserved when ever we interact with the original Subscriber, so we wrap it here
        // with the original contextMap. Otherwise some other context may leak into this subscriber chain from the other
        // side of the asynchronous boundary.
        final Subscriber operatorSubscriber =
                contextProvider.wrapCompletableSubscriberAndCancellable(subscriber, contextMap);
        final Subscriber upstreamSubscriber = apply(operatorSubscriber);
        original.delegateSubscribe(upstreamSubscriber, contextMap, contextProvider);
    }
}
