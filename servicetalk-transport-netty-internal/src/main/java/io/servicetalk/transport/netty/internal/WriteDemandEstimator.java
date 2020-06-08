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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;

/**
 * Provides an estimate for the value of {@code n} for calls to {@link PublisherSource.Subscription#request(long)} per
 * {@link PublisherSource.Subscription}.
 * A new {@link WriteDemandEstimator} is created for each {@link Publisher} that is written.
 *
 * <em>Any method of an instance of {@link WriteDemandEstimator} will never be called concurrently with the same or
 * other methods of the same instance.</em>
 * This means that implementations do not have to worry about thread-safety.
 */
public interface WriteDemandEstimator {
    /**
     * Callback whenever an item is written on the connection.
     * <p>
     * Write buffer capacity may not correctly reflect size of the object written.
     * Hence capacity before may not necessarily be more than capacity after write.
     *
     * @param written Item that was written.
     * @param writeBufferCapacityBeforeWrite Capacity of the write buffer before this item was written.
     * @param writeBufferCapacityAfterWrite Capacity of the write buffer after this item was written.
     */
    void onItemWrite(Object written, long writeBufferCapacityBeforeWrite, long writeBufferCapacityAfterWrite);

    /**
     * Given the current capacity of the write buffer, supply how many items to request next from the associated
     * {@link PublisherSource.Subscription}.
     * <p>
     * This method is invoked every time there could be a need to request more items from the write
     * {@link Publisher}.
     * This means that the supplied {@code writeBufferCapacityInBytes} may include the capacity for which we have
     * already requested the write {@link Publisher}.
     *
     * @param writeBufferCapacityInBytes Current write buffer capacity. This will always be non-negative and will
     * be 0 if buffer is full.
     *
     * @return Number of items to request next from the associated {@link PublisherSource.Subscription}.
     * Implementation may assume that whatever is returned here is sent as-is to the associated
     * {@link PublisherSource.Subscription}.
     */
    long estimateRequestN(long writeBufferCapacityInBytes);
}
