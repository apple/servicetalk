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
package io.servicetalk.opentracing.inmemory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static io.servicetalk.opentracing.internal.utils.MathUtil.safeFindNextPositivePowerOfTwo;

/**
 * Utility methods for sampling strategies.
 */
public final class SamplingStrategies {
    private SamplingStrategies() {
    }

    /**
     * Sample unless explicitly requested to not sample.
     * @return A filter which samples unless explicitly requested to not sample.
     */
    public static BiFunction<String, Boolean, Boolean> sampleUnlessFalse() {
        return (traceId, sampleRequested) -> sampleRequested == null || sampleRequested;
    }

    /**
     * Filter that samples as requested, but if there is no request will sample approximately {@code sampleCountHint}
     * times.
     * @param sampleCountHint Provides a hint at how many times we sample when sampling is not specified.
     * @return A filter that samples as requested, but if there is no request will sample approximately
     * {@code sampleCountHint} times.
     */
    public static BiFunction<String, Boolean, Boolean> sampleRespectRequestOrEveryN(int sampleCountHint) {
        final AtomicInteger sampleCount = new AtomicInteger();
        final int sampleCountMask = safeFindNextPositivePowerOfTwo(sampleCountHint) - 1;
        return (traceId, sampleRequested) ->
                (sampleRequested == null && (sampleCount.incrementAndGet() & sampleCountMask) == 0) ||
                (sampleRequested != null && sampleRequested);
    }

    /**
     * Filter that samples when requested, or otherwise treats no request and {@code false} as the same and will filter
     * approximately {@code sampleCountHint} times.
     * @param sampleCountHint Provides a hint at how many times we sample when sampling is {@code false} or
     * {@code null}.
     * @return A filter that if requested, don't filter if not requested, but if requested is {@code null} a sample will
     * be done roughly every {@code sampleCountHint} times.
     */
    public static BiFunction<String, Boolean, Boolean> sampleWhenRequestedOrEveryN(int sampleCountHint) {
        final AtomicInteger sampleCount = new AtomicInteger();
        final int sampleCountMask = safeFindNextPositivePowerOfTwo(sampleCountHint) - 1;
        return (traceId, sampleRequested) ->
                (sampleRequested != null && sampleRequested) || (sampleCount.incrementAndGet() & sampleCountMask) == 0;
    }
}
