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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.concurrent.api.Publisher;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A specialized {@link FlushStrategyHolder} that also receives read notifications from the connection
 * on which the {@link Publisher} inside this holder will be written.
 *
 * @param <T> Type of elements emitted by the {@link Publisher} contained in this holder.
 */
public interface ReadAwareFlushStrategyHolder<T> extends FlushStrategyHolder<T> {

    /**
     * Start of a read operation on the connection on which the {@link Publisher} inside this holder is written.
     * <p>
     *     This method will never be called concurrently with {@link #readComplete()}.
     * @param readInProgressSupplier A {@link Supplier} to fetch the current status of reads.
     *                               This {@link Supplier} will return {@link Boolean#TRUE} if a read is in progress.
     */
    void setReadInProgressSupplier(BooleanSupplier readInProgressSupplier);

    /**
     * End of a read operation on the channel on which the {@link Publisher} inside this holder is written.
     * <p>
     *     This method will never be called concurrently, even with {@link #setReadInProgressSupplier(BooleanSupplier)}.
     */
    void readComplete();

    /**
     * Creates a {@link FlushStrategy} that will flush any pending writes on the connection at the following points:
     * <ul>
     *     <li>If a read is in progress on the connection that the write is performed, then all writes till the read is completed, will be flushed together.</li>
     *     <li>If no read is in progress, every write is flushed immediately i.e. it behaves the same as {@link FlushStrategy#flushOnEach()}</li>
     *     <li>If a read is in progress and the number of writes exceeds {@code maxPendingWrites} without read complete, then those writes are flushed.</li>
     * </ul>
     *
     * @param maxPendingWrites Number of writes pending before a flush, when a read is in progress.
     * @return New {@link FlushStrategy}.
     */
    static FlushStrategy flushOnReadComplete(int maxPendingWrites) {
        return new FlushOnReadCompleteStrategy(maxPendingWrites);
    }
}
