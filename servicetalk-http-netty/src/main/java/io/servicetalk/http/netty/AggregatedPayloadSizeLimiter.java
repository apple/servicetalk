/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.PayloadTooLargeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import javax.annotation.Nullable;

import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Bounds the size of an aggregated HTTP payload body. Created once per client/server (see
 * {@link ReadOnlyHttpClientConfig} / {@link ReadOnlyHttpServerConfig}) and shared across all of its connections, so the
 * warn-only throttle state below is naturally scoped per client/server.
 * <p>
 * It is supplied to {@code servicetalk-http-api} as a {@link LongConsumer} (rather than a dedicated type) so that no
 * new public API is introduced: {@code HttpDataSourceTransformations} invokes {@link #accept(long)} with the running
 * aggregated size as each buffer arrives.
 */
final class AggregatedPayloadSizeLimiter implements LongConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AggregatedPayloadSizeLimiter.class);
    private static final long WARN_INTERVAL_NANOS = MINUTES.toNanos(5);

    /**
     * A no-op limiter that never rejects or warns, regardless of payload size.
     */
    static final LongConsumer NONE = size -> { };

    private final int maxAggregatedSize;
    // Non-null iff this is a warn-only limiter. Holds nanoTime() of the last emitted warning so we can rate-limit to
    // one entry per WARN_INTERVAL_NANOS. Shared across all aggregations of the owning client/server.
    @Nullable
    private final AtomicLong lastWarnNanos;

    private AggregatedPayloadSizeLimiter(final int maxAggregatedSize, final boolean warnOnly) {
        this.maxAggregatedSize = maxAggregatedSize;
        // Seed in the past so the first time the limit is exceeded a warning is emitted immediately.
        this.lastWarnNanos = warnOnly ? new AtomicLong(nanoTime() - WARN_INTERVAL_NANOS) : null;
    }

    /**
     * Create a limiter that <strong>rejects</strong> aggregated payloads larger than {@code maxAggregatedSize} by
     * throwing a {@link PayloadTooLargeException}.
     *
     * @param maxAggregatedSize the maximum number of payload bytes to buffer; {@code 0} or negative disables the limit
     * @return a limiter, or {@link #NONE} when {@code maxAggregatedSize <= 0}
     */
    static LongConsumer enforcing(final int maxAggregatedSize) {
        return maxAggregatedSize <= 0 ? NONE : new AggregatedPayloadSizeLimiter(maxAggregatedSize, false);
    }

    /**
     * Create a limiter that logs a rate-limited warning when an aggregated payload exceeds {@code maxAggregatedSize}
     * but otherwise lets it through. Warnings are throttled to one entry per five minutes for this limiter (and hence
     * per client/server).
     *
     * @param maxAggregatedSize the size in bytes above which a warning is emitted; {@code 0} or negative disables it
     * @return a limiter, or {@link #NONE} when {@code maxAggregatedSize <= 0}
     */
    static LongConsumer warning(final int maxAggregatedSize) {
        return maxAggregatedSize <= 0 ? NONE : new AggregatedPayloadSizeLimiter(maxAggregatedSize, true);
    }

    /**
     * Invoked with the running aggregated payload size (in bytes) as each buffer is accumulated. In enforcing mode
     * throws once the accumulated payload would exceed the limit; in warn-only mode emits a rate-limited warning and
     * returns normally so aggregation can continue.
     *
     * @param totalSize the number of payload bytes accumulated so far, including the buffer about to be appended
     */
    @Override
    public void accept(final long totalSize) {
        if (totalSize <= maxAggregatedSize) {
            return;
        }
        if (lastWarnNanos == null) {
            throw new PayloadTooLargeException("Maximum aggregated payload size=" + maxAggregatedSize +
                    " total payload size=" + totalSize);
        }
        maybeWarn(totalSize);
    }

    private void maybeWarn(final long totalSize) {
        assert lastWarnNanos != null;
        final long now = nanoTime();
        final long last = lastWarnNanos.get();
        if (now - last >= WARN_INTERVAL_NANOS && lastWarnNanos.compareAndSet(last, now)) {
            LOGGER.warn("Aggregated payload size={} exceeded the configured maximum of {} bytes, but the limit is " +
                    "configured in warn-only mode so the payload is allowed through. Configure an enforcing " +
                    "maxAggregatedPayloadSize(int) to reject oversized payloads. This warning is rate-limited to " +
                    "once per 5 minutes per client/server.", totalSize, maxAggregatedSize);
        }
    }
}
