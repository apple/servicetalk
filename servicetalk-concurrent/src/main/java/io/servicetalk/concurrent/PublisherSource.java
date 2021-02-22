/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
 * An asynchronous computation that emits zero or more items to its {@link Subscriber} and may or may not terminate
 * successfully or with an error.
 * <p>
 * This is a replica of the APIs provided by
 * <a href="https://github.com/reactive-streams/reactive-streams-jvm">Reactive Streams</a> and follows the
 * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#specification">
 * Reactive Streams specifications</a>.
 * All implementations of this {@code PublisherSource} adhere to the rules as specified for a Reactive Streams
 * {@code Publisher} in
 * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#1-publisher-code">
 * Section 1</a> of the specifications.
 *
 * @param <T> Type of the items emitted by this {@code PublisherSource}.
 */
@FunctionalInterface
public interface PublisherSource<T> {

    /**
     * Subscribe for the result(s) of this {@code PublisherSource}.
     *
     * @param subscriber to subscribe for the result.
     */
    void subscribe(Subscriber<? super T> subscriber);

    /**
     * A subscriber of result(s) produced by a {@code PublisherSource}.
     * <p>
     * This is a replica of the APIs provided by
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm">Reactive Streams</a> and follows the
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#specification">
     * Reactive Streams specifications</a>.
     * All implementations of this {@code Subscriber} adhere to the rules as specified for a Reactive Streams
     * {@code Subscriber} in
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#2-subscriber-code">
     * Section 2</a> of the specifications.
     *
     * @param <T> Type of items received by this {@code Subscriber}.
     */
    interface Subscriber<T> {

        /**
         * Callback to receive a {@link Subscription} for this {@code Subscriber}.
         * <p>
         * See
         * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#2-subscriber-code">
         * Reactive Streams specifications</a> for the rules about how and when this method will be invoked.
         *
         * @param subscription {@link Subscription} for this {@code Subscriber}.
         */
        void onSubscribe(Subscription subscription);

        /**
         * Callback to receive a {@link T data} element for this {@code Subscriber}.
         * <p>
         * See
         * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#2-subscriber-code">
         * Reactive Streams specifications</a> for the rules about how and when this method will be invoked.
         *
         * @param t A {@link T data} element.
         */
        void onNext(@Nullable T t);

        /**
         * Callback to receive an {@link Throwable error} for this {@code Subscriber}.
         * <p>
         * See
         * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#2-subscriber-code">
         * Reactive Streams specifications</a> for the rules about how and when this method will be invoked.
         *
         * @param t {@link Throwable error} for this {@code Subscriber}.
         */
        void onError(Throwable t);

        /**
         * Callback to signal completion of the {@link PublisherSource} for this {@code Subscriber}.
         * <p>
         * See
         * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#2-subscriber-code">
         * Reactive Streams specifications</a> for the rules about how and when this method will be invoked.
         */
        void onComplete();
    }

    /**
     * A subscription to control the signals emitted from a {@link PublisherSource} to a {@link Subscriber}.
     * <p>
     * This is a replica of the APIs provided by
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm">Reactive Streams</a> and follows the
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#specification">
     * Reactive Streams specifications</a>.
     * All implementations of this {@code Subscription} adhere to the rules as specified for a Reactive Streams
     * {@code Subscription} in
     * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3-subscription-code">
     * Section 3</a> of the specifications.
     */
    interface Subscription extends Cancellable {

        /**
         * Requests {@code n} more items from the associated {@link PublisherSource} for the associated
         * {@link Subscriber}.
         * <p>
         * See
         * <a href="https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#3-subscription-code">
         * Reactive Streams specifications</a> for the rules about how and when this method will be invoked.
         *
         * @param n Number of items to request.
         */
        void request(long n);
    }

    /**
     * A {@link Processor} represents a processing stage that is both a {@link PublisherSource} and a {@link Subscriber}
     * and obeys the contracts of both.
     *
     * @param <T> The type of {@link Subscriber}.
     * @param <R> the type of {@link PublisherSource}.
     */
    interface Processor<T, R> extends PublisherSource<R>, PublisherSource.Subscriber<T> {
    }
}
