/**
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
package io.servicetalk.concurrent;

import javax.annotation.Nullable;

/**
 * An asynchronous computation that either completes with success giving the result or completes with an error.
 *
 * @param <T> Type of the result of the single.
 */
public interface Single<T> {

    /**
     * Subscribe for the result of this {@code Single}.
     *
     * @param subscriber to subscribe for the result.
     * @see org.reactivestreams.Publisher#subscribe(org.reactivestreams.Subscriber)
     */
    void subscribe(Subscriber<? super T> subscriber);

    /**
     * Subscriber of the outcome of a {@link Single}.
     * <p>
     * The semantics and threading model of this interface is meant to be the same as {@link Subscriber},
     * but simplified for the use case where the operations completes with a single data element or fails.
     */
    interface Subscriber<T> {
        /**
         * Called when the associated {@link Single} is subscribed via {@link Single#subscribe(Subscriber)}.
         * @param cancellable A {@link Cancellable} that can be used to cancel the asynchronous computation for
         * this subscriber.
         */
        void onSubscribe(Cancellable cancellable);

        /**
         * Success terminal state.
         * <p>
         * No further events will be sent.
         *
         * @param result of the {@link Single}.
         */
        void onSuccess(@Nullable T result);

        /**
         * Failed terminal state.
         * <p>
         * No further events will be sent.
         *
         * @param t the throwable signaled.
         */
        void onError(Throwable t);
    }
}
