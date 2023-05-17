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

import static io.servicetalk.utils.internal.DurationUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;

/**
 * A factory to create {@link KeepAlivePolicy} instances.
 */
public final class H2KeepAlivePolicies {
    static final Duration DEFAULT_IDLE_DURATION = ofSeconds(30);
    static final Duration DEFAULT_ACK_TIMEOUT = ofSeconds(5);
    private static final KeepAlivePolicy DISABLE_KEEP_ALIVE =
            new DefaultKeepAlivePolicy(Duration.ZERO, DEFAULT_ACK_TIMEOUT, false);

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
     * of the returned {@link KeepAlivePolicy}.
     *
     * @param idleDuration {@link Duration} of idleness on a connection after which a
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent.
     * @return A {@link KeepAlivePolicy} that sends a <a href="https://tools.ietf.org/html/rfc7540#section-6.7">
     * ping</a> if the channel is idle for the passed {@code idleDuration}.
     * @see KeepAlivePolicy#idleDuration()
     */
    public static KeepAlivePolicy whenIdleFor(final Duration idleDuration) {
        ensureNonNegative(idleDuration, "idleDuration");
        if (idleDuration.isZero()) {
            return disabled();
        }
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
     * This value is recommended to be greater than or equal to {@code ackTimeout}.
     * @param ackTimeout {@link Duration} to wait for an acknowledgment of a previously sent
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a>.
     * This value is recommended to be less than or equal to {@code idleDuration}.
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
        private Duration ackTimeout = DEFAULT_ACK_TIMEOUT;
        private boolean withoutActiveStreams;

        /**
         * Set the {@link Duration} of idleness on a connection after which a
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent.
         * <p>
         * Too short ping durations can be used for testing but may cause unnecessarily high network traffic in real
         * environments. {@link Duration#ZERO} disables keep-alive {@code PING} frames.
         *
         * @param idleDuration {@link Duration} of idleness on a connection after which a
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent or {@link Duration#ZERO} to
         * disable keep-alive {@code PING} frames.
         * @return {@code this}.
         * @see KeepAlivePolicy#idleDuration()
         */
        public KeepAlivePolicyBuilder idleDuration(final Duration idleDuration) {
            this.idleDuration = ensureNonNegative(idleDuration, "idleDuration");
            return this;
        }

        /**
         * Set the maximum {@link Duration} to wait for an acknowledgment of a previously sent
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a>. If no acknowledgment is received, within
         * the configured timeout, a connection will be closed.
         * <p>
         * This duration must be positive. Too short ack timeout can cause undesirable connection closures. Too long ack
         * timeout can add unnecessary delay when the remote peer is unresponsive or the network connection is broken,
         * because under normal circumstances {@code PING} frames ara acknowledged immediately.
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
            return new DefaultKeepAlivePolicy(idleDuration, ackTimeout, withoutActiveStreams);
        }
    }

    static KeepAlivePolicy validateKeepAlivePolicy(final KeepAlivePolicy keepAlivePolicy) {
        requireNonNull(keepAlivePolicy, "keepAlivePolicy");
        ensureNonNegative(keepAlivePolicy.idleDuration(), "idleDuration");
        ensurePositive(keepAlivePolicy.ackTimeout(), "ackTimeout");
        return keepAlivePolicy;
    }
}
