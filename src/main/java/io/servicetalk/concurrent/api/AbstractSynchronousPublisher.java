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

/**
 * Base class for all {@link Publisher}s that are created with already realized values and does not generate values asynchronously.
 *
 * @param <T> Type of items emitted.
 */
abstract class AbstractSynchronousPublisher<T> extends FailRegularSubscribePublisher<T> {

    AbstractSynchronousPublisher(Executor executor) {
        super(executor);
    }

    @Override
    final void handleSubscribe(Subscriber<? super T> subscriber, InOrderExecutor inOrderExecutor) {
        // Wrap the passed Subscriber with the InOrderExecutor to make sure they are not invoked in the thread that
        // asynchronously processes signals and hence may not be safe to execute user code.
        doSubscribe(inOrderExecutor.wrap(subscriber));
    }

    /**
     * Handles the subscribe call.
     *
     *  @param subscriber {@link Subscriber} to this {@link Publisher}.
     */
    abstract void doSubscribe(Subscriber<? super T> subscriber);
}
