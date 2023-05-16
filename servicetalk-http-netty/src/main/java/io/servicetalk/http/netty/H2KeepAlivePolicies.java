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
package io.servicetalk.http.netty;

import io.servicetalk.http.netty.H2ProtocolConfig.KeepAlivePolicy;

import java.time.Duration;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.H2KeepAlivePolicies.KeepAlivePolicyBuilder.validateIdleDuration;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.KeepAlivePolicyBuilder.validateRelativeDurations;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.DurationUtils.min;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofSeconds;

/**
 * A factory to create {@link KeepAlivePolicy} instances.
 */
public final class H2KeepAlivePolicies {
    static final KeepAlivePolicy DISABLE_KEEP_ALIVE =
            new DefaultKeepAlivePolicy(ofDays(365), ofDays(365), false);
    static final Duration DEFAULT_IDLE_DURATION = ofSeconds(30);
    static final Duration DEFAULT_ACK_TIMEOUT = ofSeconds(5);

    private H2KeepAlivePolicies() {
        // no instances.
    }

    /**
     * Returns a {@link KeepAlivePolicy} that disables all keep alive behaviors.
     *
     * @return A {@link KeepAlivePolicy} that disables all keep alive behaviors.
     */
    public static KeepAlivePolicy disabled() {
        return DISABLE_KEEP_ALIVE;
    }

    /**
     * Returns a {@link KeepAlivePolicy} that sends a <a href="https://tools.ietf.org/html/rfc7540#section-6.7">
     * ping</a> if the channel is idle for the passed {@code idleDuration}. Default values are used for other parameters
     * of the returned {@link KeepAlivePolicy}. Default value for {@link KeepAlivePolicy#ackTimeout()} can be adjusted
     * if the passed {@code idleDuration} is less.
     *
     * @param idleDuration {@link Duration} of idleness on a connection after which a
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent.
     * @return A {@link KeepAlivePolicy} that sends a <a href="https://tools.ietf.org/html/rfc7540#section-6.7">
     * ping</a> if the channel is idle for the passed {@code idleDuration}.
     * @see KeepAlivePolicy#idleDuration()
     */
    public static KeepAlivePolicy whenIdleFor(final Duration idleDuration) {
        return new KeepAlivePolicyBuilder().idleDuration(idleDuration).build();
    }

    /**
     * Returns a {@link KeepAlivePolicy} that sends a <a href="https://tools.ietf.org/html/rfc7540#section-6.7">
     * ping</a> if the channel is idle for the passed {@code idleDuration} and waits for {@code ackTimeout} for an ack
     * for that <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a>. Default values are used for other
     * parameters of the returned {@link KeepAlivePolicy}.
     *
     * @param idleDuration {@link Duration} of idleness on a connection after which a
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent.
     * This value must be greater than or equal to {@code ackTimeout}.
     * @param ackTimeout {@link Duration} to wait for an acknowledgment of a previously sent
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a>.
     * This value must be less than or equal to {@code idleDuration}.
     * @return A {@link KeepAlivePolicy} that sends a <a href="https://tools.ietf.org/html/rfc7540#section-6.7">
     * ping</a> if the channel is idle for the passed {@code idleDuration} and waits for {@code ackTimeout} for an ack
     * for that <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a>
     * @see KeepAlivePolicy#idleDuration()
     * @see KeepAlivePolicy#ackTimeout()
     */
    public static KeepAlivePolicy whenIdleFor(final Duration idleDuration, final Duration ackTimeout) {
        return new KeepAlivePolicyBuilder().idleDuration(idleDuration).ackTimeout(ackTimeout).build();
    }

    /**
     * A builder of {@link KeepAlivePolicy}.
     */
    public static final class KeepAlivePolicyBuilder {
        private Duration idleDuration = DEFAULT_IDLE_DURATION;
        @Nullable
        private Duration ackTimeout;
        private boolean withoutActiveStreams;

        /**
         * Set the {@link Duration} of idleness on a connection after which a
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent.
         * <p>
         * Too short ping durations can be used for testing but may cause unnecessarily high network traffic in real
         * environments. The minimum allowed value is 1 second.
         * <p>
         * This duration can not be smaller than {@link #ackTimeout(Duration)}. The system expects to receive
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> acknowledgment before it can send the
         * following ping frames.
         *
         * @param idleDuration {@link Duration} of idleness on a connection after which a
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent.
         * @return {@code this}.
         * @see KeepAlivePolicy#idleDuration()
         */
        public KeepAlivePolicyBuilder idleDuration(final Duration idleDuration) {
            this.idleDuration = validateIdleDuration(idleDuration);
            return this;
        }

        /**
         * Set the maximum {@link Duration} to wait for an acknowledgment of a previously sent
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a>. If no acknowledgment is received, the
         * connection will be closed.
         * <p>
         * This duration can not be greater than {@link #idleDuration(Duration)}. The system expects to receive
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> acknowledgment before it can send the
         * following ping frames.
         *
         * @param ackTimeout {@link Duration} to wait for an acknowledgment of a previously sent
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a>.
         * @return {@code this}.
         * @see KeepAlivePolicy#ackTimeout()
         */
        public KeepAlivePolicyBuilder ackTimeout(final Duration ackTimeout) {
            this.ackTimeout = ensurePositive(ackTimeout, "ackTimeout");
            return this;
        }

        /**
         * Allow/disallow sending <a href="https://tools.ietf.org/html/rfc7540#section-6.7">pings</a> even
         * when no streams are <a href="https://tools.ietf.org/html/rfc7540#section-5.1">active</a>.
         *
         * @param withoutActiveStreams {@code true} if
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">pings</a> are expected when no streams are
         * <a href="https://tools.ietf.org/html/rfc7540#section-5.1">active</a>.
         * @return {@code this}.
         * @see KeepAlivePolicy#withoutActiveStreams()
         */
        public KeepAlivePolicyBuilder withoutActiveStreams(final boolean withoutActiveStreams) {
            this.withoutActiveStreams = withoutActiveStreams;
            return this;
        }

        /**
         * Build a new {@link KeepAlivePolicy}.
         *
         * @return new {@link KeepAlivePolicy}.
         */
        public KeepAlivePolicy build() {
            Duration ackTimeout = this.ackTimeout;
            if (ackTimeout == null) {
                ackTimeout = min(idleDuration, DEFAULT_ACK_TIMEOUT);
            }
            validateRelativeDurations(idleDuration, ackTimeout);
            return new DefaultKeepAlivePolicy(idleDuration, ackTimeout, withoutActiveStreams);
        }

        static Duration validateIdleDuration(final Duration idleDuration) {
            if (idleDuration.getSeconds() < 1) {
                throw new IllegalArgumentException("idleDuration: " + idleDuration + " (expected: >= 1 second)");
            }
            return idleDuration;
        }

        static void validateRelativeDurations(final Duration idleDuration, final Duration ackTimeout) {
            if (idleDuration.compareTo(ackTimeout) < 0) {
                throw new IllegalArgumentException("idleDuration can not be less than ackTimeout, idleDuration: " +
                        idleDuration + ", ackTimeout: " + ackTimeout);
            }
        }
    }

    @Nullable
    static KeepAlivePolicy validateKeepAlivePolicy(@Nullable final KeepAlivePolicy keepAlivePolicy) {
        if (keepAlivePolicy == null) {
            return null;
        }
        validateIdleDuration(keepAlivePolicy.idleDuration());
        ensurePositive(keepAlivePolicy.ackTimeout(), "ackTimeout");
        validateRelativeDurations(keepAlivePolicy.idleDuration(), keepAlivePolicy.ackTimeout());
        return keepAlivePolicy;
    }
}
