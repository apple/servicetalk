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
package io.servicetalk.concurrent;

import javax.annotation.Nullable;

/**
 * An asynchronous computation that either completes with success giving the result or completes with an error.
 *
 * @param <T> Type of the result of this {@code SingleSource}.
 */
public interface SingleSource<T> {

    /**
     * Subscribe for the result of this {@code SingleSource}.
     *
     * @param subscriber to subscribe for the result.
     * @see PublisherSource#subscribe(PublisherSource.Subscriber)
     */
    void subscribe(Subscriber<? super T> subscriber);

    /**
     * Subscriber of the outcome of a {@link SingleSource}.
     * <p>
     * The semantics and threading model of this interface is meant to be the same as
     * {@link PublisherSource.Subscriber}, but simplified for the use case where the operations completes with a single
     * data element or fails.
     *
     * @param <T> Type of the result of the {@link SingleSource}.
     */
    interface Subscriber<T> {
        /**
         * Called when the associated {@link SingleSource} is subscribed via {@link SingleSource#subscribe(Subscriber)}.
         * @param cancellable A {@link Cancellable} that can be used to cancel the asynchronous computation for
         * this subscriber.
         */
        void onSubscribe(Cancellable cancellable);

        /**
         * Success terminal state.
         * <p>
         * No further events will be sent.
         *
         * @param result of the {@link SingleSource}.
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

    /**
     * An entity that is both {@link SingleSource} and {@link Subscriber}.
     * This is same as {@link PublisherSource.Processor} but for {@link SingleSource}s.
     * @param <T> The type of {@link Subscriber}.
     * @param <R> The type of {@link SingleSource}.
     */
    interface Processor<T, R> extends SingleSource<R>, Subscriber<T> {
    }
}
