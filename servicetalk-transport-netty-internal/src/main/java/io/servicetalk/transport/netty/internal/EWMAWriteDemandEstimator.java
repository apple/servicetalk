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

import static io.servicetalk.transport.netty.internal.OverlappingCapacityAwareEstimator.SizeEstimator.defaultEstimator;
import static java.lang.Math.max;

final class EWMAWriteDemandEstimator extends OverlappingCapacityAwareEstimator {
    private static final double WEIGHT_NEW = 1 / 5d;
    private static final double WEIGHT_HISTORICAL = 1 - WEIGHT_NEW;
    private long sizeAccumulator;

    EWMAWriteDemandEstimator() {
        this(1024);
    }

    EWMAWriteDemandEstimator(long sizeAccumulator) {
        this(sizeAccumulator, defaultEstimator());
    }

    private EWMAWriteDemandEstimator(long sizeAccumulator, SizeEstimator sizeEstimator) {
        super(sizeEstimator);
        if (sizeAccumulator <= 0) {
            throw new IllegalArgumentException("sizeAccumulator: " + sizeAccumulator + " (expected >0)");
        }
        this.sizeAccumulator = sizeAccumulator;
    }

    @Override
    protected void recordSize(final Object written, final long sizeInBytes) {
        sizeAccumulator = max((long) (WEIGHT_NEW * sizeInBytes + WEIGHT_HISTORICAL * sizeAccumulator), 1L);
    }

    @Override
    protected long getRequestNForCapacity(final long capacityToFill) {
        return capacityToFill / sizeAccumulator;
    }
}
