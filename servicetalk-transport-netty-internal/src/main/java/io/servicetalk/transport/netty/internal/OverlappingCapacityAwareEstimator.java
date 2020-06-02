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

import io.netty.channel.FileRegion;

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static java.util.Objects.requireNonNull;

/**
 * An implementation of {@link WriteDemandEstimator} that stores the last seen write buffer capacity as
 * provided by {@link #estimateRequestN(long)} and only fills any increase in capacity in a subsequent call to
 * {@link #estimateRequestN(long)}.
 */
abstract class OverlappingCapacityAwareEstimator implements WriteDemandEstimator {

    private final SizeEstimator sizeEstimator;
    private long lastSeenCapacity;
    private long outstandingRequested;

    protected OverlappingCapacityAwareEstimator(SizeEstimator sizeEstimator) {
        this.sizeEstimator = requireNonNull(sizeEstimator);
    }

    @Override
    public final void onItemWrite(Object written, long writeBufferCapacityBeforeWrite,
                                  long writeBufferCapacityAfterWrite) {
        if (outstandingRequested > 0) {
            outstandingRequested--;
        }
        long size = sizeEstimator.estimateSize(written, writeBufferCapacityBeforeWrite, writeBufferCapacityAfterWrite);
        if (size > 0) {
            recordSize(written, size);
        }
    }

    @Override
    public final long estimateRequestN(long writeBufferCapacityInBytes) {
        assert writeBufferCapacityInBytes >= 0 : "Write buffer capacity must be non-negative.";
        long capacityToFill = outstandingRequested == 0 ? writeBufferCapacityInBytes : writeBufferCapacityInBytes -
                lastSeenCapacity;
        lastSeenCapacity = writeBufferCapacityInBytes;
        // Request the number of items that can fill the extra write buffer capacity since last requested.
        long toRequest = 0;
        if (capacityToFill > 0) {
            toRequest = getRequestNForCapacity(capacityToFill);
            if (toRequest == 0 && outstandingRequested == 0) {
                // If we have capacity and for some reason getRequestNForCapacity returned 0, then at least request 1
                // otherwise we may never get any more data and we may never request again.
                toRequest = 1;
            }
            outstandingRequested = addWithOverflowProtection(outstandingRequested, toRequest);
        }
        return toRequest;
    }

    protected abstract void recordSize(Object written, long sizeInBytes);

    protected abstract long getRequestNForCapacity(long capacityToFill);

    /**
     * An object size estimator based on write buffer capacity before and after an item write.
     */
    @FunctionalInterface
    interface SizeEstimator {

        /**
         * Given the write buffer capacity before an after the write of an item, estimate the size of the item.
         *<p>
         * Write buffer capacity may not correctly reflect size of the object written.
         * Hence capacity before may not necessarily be more than capacity after write.
         *
         * @param written Item written.
         * @param writeBufferCapacityBeforeWrite Write buffer capacity before writing this item.
         * @param writeBufferCapacityAfterWrite Write buffer capacity after writing this item.
         *
         * @return Estimated size of the item.
         */
        long estimateSize(Object written, long writeBufferCapacityBeforeWrite, long writeBufferCapacityAfterWrite);

        /**
         * Returns the default {@link SizeEstimator} to use.
         *
         * @return The default {@link SizeEstimator} to use.
         */
        static SizeEstimator defaultEstimator() {
            return (written, before, after) -> {
                if (written instanceof FileRegion) {
                    return ((FileRegion) written).count();
                }
                return before > after ? before - after : 0;
            };
        }
    }
}
