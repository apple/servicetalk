/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

/**
 * An asynchronous computation that does not emit any data. It just completes or emits an error.
 */
@FunctionalInterface
public interface CompletableSource {

    /**
     * Subscribes to the outcome of this {@code CompletableSource}.
     *
     * @param subscriber of the outcome.
     * @see PublisherSource#subscribe(PublisherSource.Subscriber)
     */
    void subscribe(Subscriber subscriber);

    /**
     * Subscriber of the outcome of a {@link Cancellable}.
     * <p>
     * The semantics and threading model of this interface is meant to be the same as
     * {@link PublisherSource.Subscriber}, but simplified for the use case where the operations completes or fails with
     * no data.
     */
    interface Subscriber {
        /**
         * Called when the associated {@link CompletableSource} is subscribed via
         * {@link CompletableSource#subscribe(Subscriber)}.
         * @param cancellable A {@link Cancellable} that can be used to cancel the asynchronous computation for
         * this subscriber.
         */
        void onSubscribe(Cancellable cancellable);

        /**
         * Success terminal state.
         * <p>
         * No further events will be sent.
         */
        void onComplete();

        /**
         * Failed terminal state.
         * <p>
         * No further events will be sent.
         *
         * @param t the throwable signaled
         */
        void onError(Throwable t);
    }

    /**
     * An entity that is both {@link CompletableSource} and {@link Subscriber}.
     * This is same as {@link PublisherSource.Processor} but for {@link CompletableSource}s.
     */
    interface Processor extends CompletableSource, Subscriber {
    }
}
