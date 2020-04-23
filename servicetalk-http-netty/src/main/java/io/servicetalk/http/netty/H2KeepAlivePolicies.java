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

import static java.time.Duration.ofDays;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;

/**
 * A factory to create {@link KeepAlivePolicy} instances.
 */
public final class H2KeepAlivePolicies {
    static final KeepAlivePolicy DISABLE_KEEP_ALIVE =
            new DefaultKeepAlivePolicy(ofDays(365), ofDays(365), false);
    static final Duration DEFAULT_IDLE_DURATION = ofSeconds(30);
    static final Duration DEFAULT_ACK_TIMEOUT = ofSeconds(30);

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
     * @param ackTimeout {@link Duration} to wait for an acknowledgment of a previously sent
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a>.
     * @return A {@link KeepAlivePolicy} that sends a <a href="https://tools.ietf.org/html/rfc7540#section-6.7">
     * ping</a> if the channel is idle for the passed {@code idleDuration} and waits for {@code ackTimeout} for an ack
     * for that <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a>
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
         * <strong>Too short ping durations may cause high network traffic, so a minimum duration may be
         * enforced.</strong>
         *
         * @param idleDuration {@link Duration} of idleness on a connection after which a
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a> is sent.
         * @return {@code this}.
         * @see KeepAlivePolicy#idleDuration()
         */
        public KeepAlivePolicyBuilder idleDuration(final Duration idleDuration) {
            if (idleDuration.getSeconds() < 10 || idleDuration.toDays() > 1) {
                throw new IllegalArgumentException("idleDuration: " + idleDuration +
                        " (expected >= 10 seconds and < 1 day)");
            }
            this.idleDuration = idleDuration;
            return this;
        }

        /**
         * Set the maximum {@link Duration} to wait for an acknowledgment of a previously sent
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a>. If no acknowledgment is received, the
         * connection will be closed.
         *
         * @param ackTimeout {@link Duration} to wait for an acknowledgment of a previously sent
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.7">ping</a>.
         * @return {@code this}.
         * @see KeepAlivePolicy#ackTimeout()
         */
        public KeepAlivePolicyBuilder ackTimeout(final Duration ackTimeout) {
            this.ackTimeout = requireNonNull(ackTimeout);
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
}
