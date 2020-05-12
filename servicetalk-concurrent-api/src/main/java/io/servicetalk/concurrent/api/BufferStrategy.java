/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import javax.annotation.Nullable;

/**
 * A strategy for {@link Publisher#buffer(BufferStrategy) buffering} items emitted from a {@link Publisher}.
 * <p>
 * A buffer strategy represents {@link #boundaries() asynchronous buffer boundaries} over which items from a
 * {@link Publisher} are buffered. Each item emitted from the boundary {@link Publisher} represents the end of the
 * last boundary and start of the next boundary. The first item emitted from this {@link Publisher} is treated as the
 * start of the first boundary and {@link Publisher#buffer(BufferStrategy)} may decide to defer requesting items from
 * the {@link Publisher} on which that operator is applied.
 *
 * @param <T> items emitted from the {@link Publisher} which are to be buffered using this {@link BufferStrategy}.
 * @param <BC> An intermediate mutable object that holds the items into a buffer before it is emitted.
 * @param <B> The buffer of items.
 */
public interface BufferStrategy<T, BC extends BufferStrategy.Accumulator<T, B>, B> {

    /**
     * Returns a {@link Publisher} representing asynchronous buffer boundaries. This {@link Publisher} is expected to be
     * an infinite {@link Publisher}. Hence, it should never terminate any {@link Subscriber} subscribed to it. Instead
     * {@link Subscriber}s will always {@link Subscription#cancel() cancel} their {@link Subscription}. If this
     * expectation is violated, buffered items may be discarded.
     *
     * @return A {@link Publisher} representing asynchronous buffer boundaries.
     */
    Publisher<BC> boundaries();

    /**
     * A rough estimate of the number of items in a buffer.
     *
     * @return A rough estimate of the number of items in a buffer.
     */
    int bufferSizeHint();

    /**
     * An intermediate mutable object that holds items till it is {@link #finish() finished}.
     * <p>
     * None of the methods on an instance of an {@link Accumulator} will be invoked concurrently and no other method
     * will be called after {@link #finish()} returns.
     *
     * @param <T> Type of items added to this {@link Accumulator}.
     * @param <B> Type of item created when an accumulation is {@link #finish() finished}.
     */
    interface Accumulator<T, B> {
        /**
         * Adds the passed {@code item} to this {@link Accumulator}.
         *
         * @param item to add to this {@link Accumulator}.
         */
        void accumulate(@Nullable T item);

        /**
         * Finishes accumulation and returns the accumulated value.
         *
         * @return Accumulated value.
         */
        B finish();
    }
}
