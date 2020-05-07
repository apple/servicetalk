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

import io.servicetalk.concurrent.internal.QueueFullException;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.utils.internal.PlatformDependent.newMpscQueue;

/**
 * A static factory for {@link PublisherProcessorBuffer}s.
 */
public final class PublisherProcessorBuffers {
    private PublisherProcessorBuffers() {
        // no instances.
    }

    /**
     * Creates a new {@link PublisherProcessorBuffer} which buffers a maximum of {@code maxBuffer} items without being
     * consumed. If more items are {@link PublisherProcessorBuffer#add(Object) added} to the returned
     * {@link PublisherProcessorBuffer} then that {@link PublisherProcessorBuffer#add(Object) addition} will fail.
     *
     * @param maxBuffer Maximum number of items that can be present in the returned
     * @param <T> Type of items added to the returned {@link PublisherProcessorBuffer}.
     * @return A new {@link PublisherProcessorBuffer}.
     */
    public static <T> PublisherProcessorBuffer<T> fixedSize(final int maxBuffer) {
        return new AbstractPublisherProcessorBuffer<T, Queue<Object>>(maxBuffer, newMpscQueueForItemSize(maxBuffer)) {
            @Override
            void offerPastBufferSize(final Object signal, final Queue<Object> queue) {
                throw new QueueFullException("processor-buffer", maxBuffer);
            }
        };
    }

    /**
     * Creates a new {@link PublisherProcessorBuffer} which buffers a maximum of {@code maxBuffer} items without being
     * consumed. If more items are {@link PublisherProcessorBuffer#add(Object) added} to the returned
     * {@link PublisherProcessorBuffer} then that {@link PublisherProcessorBuffer#add(Object) addition} will be dropped.
     *
     * @param maxBuffer Maximum number of items that can be present in the returned
     * @param <T> Type of items added to the returned {@link PublisherProcessorBuffer}.
     * @return A new {@link PublisherProcessorBuffer}.
     */
    public static <T> PublisherProcessorBuffer<T> fixedSizeDropLatest(final int maxBuffer) {
        return new AbstractPublisherProcessorBuffer<T, Queue<Object>>(maxBuffer, newMpscQueueForItemSize(maxBuffer)) {
            @Override
            void offerPastBufferSize(final Object signal, final Queue<Object> queue) {
                // noop => drop latest
            }
        };
    }

    /**
     * Creates a new {@link PublisherProcessorBuffer} which buffers a maximum of {@code maxBuffer} items without being
     * consumed. If more items are {@link PublisherProcessorBuffer#add(Object) added} to the returned
     * {@link PublisherProcessorBuffer} then the oldest item previously added to the buffer will be dropped.
     *
     * @param maxBuffer Maximum number of items that can be present in the returned
     * @param <T> Type of items added to the returned {@link PublisherProcessorBuffer}.
     * @return A new {@link PublisherProcessorBuffer}.
     */
    public static <T> PublisherProcessorBuffer<T> fixedSizeDropOldest(final int maxBuffer) {
        return new AbstractPublisherProcessorBuffer<T, ConcurrentLinkedQueue<Object>>(maxBuffer,
                new ConcurrentLinkedQueue<>()) {
            @Override
            void offerPastBufferSize(final Object signal, final ConcurrentLinkedQueue<Object> queue) {
                queue.poll(); // drop oldest
                queue.offer(signal);
            }
        };
    }

    private static Queue<Object> newMpscQueueForItemSize(final int maxBuffer) {
        return newMpscQueue(2,
                // max items + 1 terminal
                addWithOverflowProtection(maxBuffer, 1));
    }
}
