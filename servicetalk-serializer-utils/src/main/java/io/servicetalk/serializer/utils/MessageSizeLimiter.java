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
package io.servicetalk.serializer.utils;

import io.servicetalk.serializer.api.MaxMessageSizeExceededException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.HOURS;

/**
 * Enforces (or warns on) the maximum size of a length-prefixed message for the streaming serializers in this package,
 * and resolves the default limit that the no-arg serializer constructors apply. Created once per serializer and shared
 * across its per-subscribe deframers, so the warn-only rate-limiting state is naturally scoped per serializer. Mirrors
 * {@code maxAggregatedPayloadSize} / {@code AggregatedPayloadSizeLimiter} for HTTP client/server.
 */
final class MessageSizeLimiter {
    static final int DEFAULT_MAX_MESSAGE_SIZE_VALUE = 16 * 1024 * 1024;
    static final String DEFAULT_MAX_MESSAGE_SIZE_PROPERTY =
            "io.servicetalk.serializer.utils.defaultMaxMessageSize";
    // Deprecated legacy property, superseded by the property above; kept for a release or two in case a deployment
    // already relies on it. The permanent property, when set, takes precedence over it.
    // FIXME: 0.43 - remove this deprecated property
    static final String LEGACY_DEFAULT_MAX_MESSAGE_SIZE_PROPERTY =
            "io.servicetalk.serializer.utils.temporaryDefaultMaxMessageSize";

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageSizeLimiter.class);
    private static final long WARN_INTERVAL_NANOS = HOURS.toNanos(2);

    /**
     * A limiter that never rejects or warns, regardless of message size (the limit is disabled).
     */
    static final MessageSizeLimiter NONE = new MessageSizeLimiter(0, false);

    // The value the 2-arg (default) serializer constructors resolve to, using the same sign convention as
    // forMaxMessageSize: 0 disables, >0 enforces, <0 warns at abs(value). Defaults to warn-only at
    // DEFAULT_MAX_MESSAGE_SIZE_VALUE bytes, overridable by the properties above.
    static final int DEFAULT_MAX_MESSAGE_SIZE;

    static {
        // The permanent property takes precedence over the deprecated legacy one, which takes precedence over the
        // built-in warn-only default.
        final Integer configured = parseDefaultOverride(DEFAULT_MAX_MESSAGE_SIZE_PROPERTY, false);
        final Integer legacy = parseDefaultOverride(LEGACY_DEFAULT_MAX_MESSAGE_SIZE_PROPERTY, true);
        DEFAULT_MAX_MESSAGE_SIZE = configured != null ? configured :
                legacy != null ? legacy : -DEFAULT_MAX_MESSAGE_SIZE_VALUE;
    }

    private final int maxMessageSize;
    // Non-null if this is a warn-only limiter.
    @Nullable
    private final AtomicLong lastWarnNanos;
    // Non-null if this is a warn-only limiter.
    @Nullable
    private final AtomicLong maxObservedSize;
    // For the shared default serializers this points at their static initialization rather than a client/server,
    // which is the best provenance available since serializers carry no owner.
    @Nullable
    private final Throwable constructionSite;

    private MessageSizeLimiter(final int maxMessageSize, final boolean warnOnly) {
        this.maxMessageSize = maxMessageSize;
        // Seed in the past so the first exceeded message warns immediately.
        this.lastWarnNanos = warnOnly ? new AtomicLong(nanoTime() - WARN_INTERVAL_NANOS) : null;
        this.maxObservedSize = warnOnly ? new AtomicLong() : null;
        this.constructionSite = warnOnly ? new Throwable(
                "Serializer with a warn-only maxMessageSize created here (not an error)") : null;
    }

    /**
     * Resolve a {@code maxMessageSize} config value into a limiter: {@code 0} disables the limit, {@code > 0} enforces
     * (rejects) at that many bytes, and {@code < 0} warns (without rejecting) at {@code abs(value)} bytes. The sign
     * selects the mode and the magnitude selects the threshold.
     */
    static MessageSizeLimiter forMaxMessageSize(final int maxMessageSize) {
        if (maxMessageSize == 0) {
            return NONE;
        }
        if (maxMessageSize > 0) {
            return new MessageSizeLimiter(maxMessageSize, false);
        }
        // -Integer.MIN_VALUE overflows back to a negative value; clamp so it stays a warn-only limiter.
        final int warnThreshold = maxMessageSize == Integer.MIN_VALUE ? Integer.MAX_VALUE : -maxMessageSize;
        return new MessageSizeLimiter(warnThreshold, true);
    }

    /**
     * Check a message's declared length against the limit. In enforcing mode throws once the length exceeds the limit;
     * in warn-only mode emits a rate-limited warning and returns normally so deserialization can continue.
     *
     * @param length the declared message length in bytes
     */
    void checkMessageSize(final int length) {
        if (maxMessageSize <= 0 || length <= maxMessageSize) {
            return;
        }
        if (lastWarnNanos == null) {
            throw new MaxMessageSizeExceededException(
                    "Message-Length " + length + " exceeds maximum " + maxMessageSize);
        }
        maybeWarn(length);
    }

    // Don't throw from the static initializer; ignore an invalid value and fall back to the built-in default so a bad
    // property can't break serializer construction (e.g. the static HttpSerializers instances).
    @Nullable
    private static Integer parseDefaultOverride(final String name, final boolean legacy) {
        final String raw = System.getProperty(name);
        if (raw == null) {
            return null;
        }
        try {
            final Integer value = Integer.valueOf(raw.trim());
            if (legacy) {
                LOGGER.warn("-D{}={} is a deprecated legacy property, superseded by -D{}, and will be removed in a " +
                        "future release; use that or set maxMessageSize per serializer via the 3-arg " +
                        "FixedLengthStreamingSerializer / VarIntLengthStreamingSerializer constructor instead.",
                        name, value, DEFAULT_MAX_MESSAGE_SIZE_PROPERTY);
            } else {
                LOGGER.debug("-D{}={}", name, value);
            }
            return value;
        } catch (NumberFormatException e) {
            LOGGER.warn("-D{}={} DANGEROUS_CONFIG_WARNING: not a valid integer; ignoring it and using the built-in " +
                    "default of {} bytes.", name, raw, DEFAULT_MAX_MESSAGE_SIZE_VALUE);
            return null;
        }
    }

    private void maybeWarn(final int length) {
        assert lastWarnNanos != null;
        assert maxObservedSize != null;
        assert constructionSite != null;
        final long maxObserved = maxObservedSize.accumulateAndGet(length, Math::max);
        final long now = nanoTime();
        final long last = lastWarnNanos.get();
        if (now - last >= WARN_INTERVAL_NANOS && lastWarnNanos.compareAndSet(last, now)) {
            LOGGER.warn("Message-Length {} exceeded the configured maximum of {} bytes, but the limit is configured " +
                    "in warn-only mode so the message is allowed through. Largest message observed so far is {} " +
                    "bytes. Configure an enforcing maxMessageSize to reject oversized messages. Rate-limited per " +
                    "serializer.", length, maxMessageSize, maxObserved, constructionSite);
        }
    }
}
