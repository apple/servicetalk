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

import io.servicetalk.transport.netty.internal.NettyConnection.RequestNSupplier;

import static io.servicetalk.transport.netty.internal.OverlappingCapacityAwareSupplier.SizeEstimator.defaultEstimator;

/**
 * An implementation of {@link RequestNSupplier} that considers maximum size recorded via {@link #recordSize(Object, long)} in a sliding window
 * to calculate the number of items required to fill a given write buffer capacity.
 */
final class MaxSizeBasedRequestNSupplier extends OverlappingCapacityAwareSupplier {

    private final long[] sizesRingBuffer;
    private final long defaultSizeInBytes;
    private int currentMaxSizeIndex;
    private int ringBufferIndex;

    MaxSizeBasedRequestNSupplier() {
        this(defaultEstimator(), 8);
    }

    MaxSizeBasedRequestNSupplier(SizeEstimator sizeEstimator, long defaultSizeInBytes) {
        this(sizeEstimator, defaultSizeInBytes, 32);
    }

    MaxSizeBasedRequestNSupplier(SizeEstimator sizeEstimator, long defaultSizeInBytes, int slidingWindowSize) {
        super(sizeEstimator);
        if (defaultSizeInBytes <= 0) {
            throw new IllegalArgumentException("defaultSizeInBytes: " + defaultSizeInBytes + " (expected > 0)");
        }
        sizesRingBuffer = new long[slidingWindowSize];
        this.defaultSizeInBytes = defaultSizeInBytes;
    }

    @Override
    protected long getRequestNForCapacity(long writeBufferCapacityInBytes) {
        return writeBufferCapacityInBytes / (sizesRingBuffer[currentMaxSizeIndex] == 0 ? defaultSizeInBytes : sizesRingBuffer[currentMaxSizeIndex]);
    }

    @Override
    protected void recordSize(Object item, long sizeInBytes) {
        // Received a new max, blindly overwrite and move on.
        if (sizeInBytes >= sizesRingBuffer[currentMaxSizeIndex]) {
            // For repeating max sizes, we always keep the latest index as max.
            currentMaxSizeIndex = ringBufferIndex;
            sizesRingBuffer[ringBufferIndex] = sizeInBytes;
            incrementRingBufferIndex();
            return;
        }

        sizesRingBuffer[ringBufferIndex] = sizeInBytes;
        if (currentMaxSizeIndex == ringBufferIndex) {
            // lost the current max, so find a new one.
            findNewMaxSizeIndex();
        }
        incrementRingBufferIndex();
    }

    private void incrementRingBufferIndex() {
        if (++ringBufferIndex == sizesRingBuffer.length) {
            ringBufferIndex = 0;
        }
    }

    private void findNewMaxSizeIndex() {
        currentMaxSizeIndex = 0;
        int i = 1; // if currentMaxSizeIndex is reset to 0, then this can start at 1
        for (; i < ringBufferIndex; ++i) {
            // Any size before and closest to ringBufferIndex will be overwritten last hence look for greater or equal max size now.
            if (sizesRingBuffer[i] >= sizesRingBuffer[currentMaxSizeIndex]) {
                currentMaxSizeIndex = i;
            }
        }
        for (; i < sizesRingBuffer.length; i++) {
            // Any size after ringBufferIndex will be overwritten first hence only look for greater max size now.
            if (sizesRingBuffer[i] > sizesRingBuffer[currentMaxSizeIndex]) {
                currentMaxSizeIndex = i;
            }
        }
    }
}
