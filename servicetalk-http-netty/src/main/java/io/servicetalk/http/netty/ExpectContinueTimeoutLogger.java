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

import io.servicetalk.transport.netty.internal.ExpectContinueTimeout;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * The {@code Expect: 100-continue} timeout of a client. Created once per client (see {@link ReadOnlyHttpClientConfig})
 * and shared across all of its connections, so the log throttle below is scoped per client.
 */
final class ExpectContinueTimeoutLogger implements ExpectContinueTimeout {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpectContinueTimeoutLogger.class);
    private static final long LOG_INTERVAL_NANOS = HOURS.toNanos(2);

    private final long timeoutNanos;
    private final LongSupplier nanoClock;
    private final AtomicLong lastLogNanos;
    private final AtomicLong timeoutsSinceLastLog = new AtomicLong();

    ExpectContinueTimeoutLogger(final long timeoutNanos) {
        this(timeoutNanos, System::nanoTime);
    }

    ExpectContinueTimeoutLogger(final long timeoutNanos, final LongSupplier nanoClock) {
        this.timeoutNanos = timeoutNanos;
        this.nanoClock = nanoClock;
        // Seed in the past so the first timeout is logged immediately.
        this.lastLogNanos = new AtomicLong(nanoClock.getAsLong() - LOG_INTERVAL_NANOS);
    }

    @Override
    public long timeoutNanos() {
        return timeoutNanos;
    }

    @Override
    public void onTimeout(final Channel channel) {
        timeoutsSinceLastLog.incrementAndGet();
        final long now = nanoClock.getAsLong();
        final long last = lastLogNanos.get();
        if (now - last >= LOG_INTERVAL_NANOS && lastLogNanos.compareAndSet(last, now)) {
            LOGGER.info("No 100 (Continue) arrived from {} within the configured {}ms, the payload body was sent " +
                    "anyway. {} requests since the last message.", channel.remoteAddress(),
                    NANOSECONDS.toMillis(timeoutNanos), timeoutsSinceLastLog.getAndSet(0));
        }
    }
}
