/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.netty.H2ProtocolConfig.KeepAlivePolicy;

import org.slf4j.event.Level;

import java.time.Duration;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.DefaultKeepAlivePolicy.DEFAULT_ACK_TIMEOUT;
import static io.servicetalk.http.netty.DefaultKeepAlivePolicy.DEFAULT_IDLE_DURATION;
import static io.servicetalk.http.netty.DefaultKeepAlivePolicy.DEFAULT_WITHOUT_ACTIVE_STREAMS;
import static io.servicetalk.http.netty.H2HeadersFactory.DEFAULT_SENSITIVITY_DETECTOR;
import static java.util.Objects.requireNonNull;

/**
 * Builder for {@link H2ProtocolConfig}.
 *
 * @see HttpProtocolConfigs#h2()
 */
public final class H2ProtocolConfigBuilder {

    private HttpHeadersFactory headersFactory = H2HeadersFactory.INSTANCE;
    private BiPredicate<CharSequence, CharSequence> headersSensitivityDetector = DEFAULT_SENSITIVITY_DETECTOR;
    @Nullable
    private String frameLoggerName;
    @Nullable
    private KeepAlivePolicy keepAlivePolicy;

    H2ProtocolConfigBuilder() {
    }

    /**
     * Sets the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding HTTP messages.
     *
     * @param headersFactory {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding HTTP
     * messages
     * @return {@code this}
     */
    public H2ProtocolConfigBuilder headersFactory(final HttpHeadersFactory headersFactory) {
        this.headersFactory = requireNonNull(headersFactory);
        return this;
    }

    /**
     * Sets the sensitivity detector to determine if a header {@code name}/{@code value} pair should be treated as
     * <a href="https://tools.ietf.org/html/rfc7541#section-7.1.3">sensitive</a>.
     *
     * @param headersSensitivityDetector the {@link BiPredicate}&lt;{@link CharSequence}, {@link CharSequence}&gt; that
     * returns {@code true} if a header &lt;{@code name}, {@code value}&gt; pair should be treated as
     * <a href="https://tools.ietf.org/html/rfc7541#section-7.1.3">sensitive</a>, {@code false} otherwise
     * @return {@code this}
     */
    public H2ProtocolConfigBuilder headersSensitivityDetector(
            final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector) {
        this.headersSensitivityDetector = requireNonNull(headersSensitivityDetector);
        return this;
    }

    /**
     * Enables a logger for HTTP/2 frames.
     * <p>
     * All frames will be logged at {@link Level#TRACE TRACE} level.
     *
     * @param loggerName the name of the logger to log HTTP/2 frames
     * @return {@code this}
     */
    public H2ProtocolConfigBuilder enableFrameLogging(final String loggerName) {
        this.frameLoggerName = requireNonNull(loggerName);
        return this;
    }

    /**
     * Configure keep alive behavior using <a href="https://tools.ietf.org/html/rfc7540#page-42">PING frames</a>.
     *
     * @return A {@link KeepAliveConfigurator} which adds keep-alive configuration to this {@link H2ProtocolConfig} when
     * {@link KeepAliveConfigurator#commit() committed}.
     */
    public KeepAliveConfigurator keepAlive() {
        return new KeepAliveConfigurator() {
            private Duration idleDuration = DEFAULT_IDLE_DURATION;
            private Duration ackTimeout = DEFAULT_ACK_TIMEOUT;
            private boolean withoutActiveStreams = DEFAULT_WITHOUT_ACTIVE_STREAMS;
            private boolean disable;

            @Override
            public KeepAliveConfigurator idleDuration(final Duration idleDuration) {
                if (idleDuration.getSeconds() < 10) {
                    throw new IllegalArgumentException("idleDuration: " + idleDuration + " (expected >= 10 seconds");
                }
                this.idleDuration = requireNonNull(idleDuration);
                return this;
            }

            @Override
            public KeepAliveConfigurator ackTimeout(final Duration ackTimeout) {
                this.ackTimeout = requireNonNull(ackTimeout);
                return this;
            }

            @Override
            public KeepAliveConfigurator withoutActiveStreams(final boolean withoutActiveStreams) {
                this.withoutActiveStreams = withoutActiveStreams;
                return this;
            }

            @Override
            public KeepAliveConfigurator disable() {
                this.disable = true;
                return this;
            }

            @Override
            public H2ProtocolConfigBuilder commit() {
                H2ProtocolConfigBuilder parent = H2ProtocolConfigBuilder.this;
                if (disable) {
                    parent.keepAlivePolicy = null;
                } else {
                    parent.keepAlivePolicy = new DefaultKeepAlivePolicy(idleDuration, ackTimeout, withoutActiveStreams);
                }
                return parent;
            }
        };
    }

    /**
     * Builds {@link H2ProtocolConfig}.
     *
     * @return {@link H2ProtocolConfig}
     */
    public H2ProtocolConfig build() {
        return new DefaultH2ProtocolConfig(headersFactory, headersSensitivityDetector, frameLoggerName,
                keepAlivePolicy);
    }

    /**
     * A configurator for {@link KeepAlivePolicy}.
     */
    public interface KeepAliveConfigurator {

        /**
         * Set the {@link Duration} of idleness on a connection after which a
         * <a href="https://tools.ietf.org/html/rfc7540#page-42">ping</a> is sent.
         * <strong>Too short ping durations may cause high network traffic, so implementations may enforce a minimum
         * duration.</strong>
         *
         * @param idleDuration {@link Duration} of idleness on a connection after which a
         * <a href="https://tools.ietf.org/html/rfc7540#page-42">ping</a> is sent.
         * @return {@code this}.
         * @see KeepAlivePolicy#idleDuration()
         */
        KeepAliveConfigurator idleDuration(Duration idleDuration);

        /**
         * Set the maximum {@link Duration} to wait for an acknowledgment of a previously sent
         * <a href="https://tools.ietf.org/html/rfc7540#page-42">ping</a>. If no acknowledgment is received, the
         * connection will be closed.
         *
         * @param ackTimeout {@link Duration} to wait for an acknowledgment of a previously sent
         * <a href="https://tools.ietf.org/html/rfc7540#page-42">ping</a>.
         * @return {@code this}.
         * @see KeepAlivePolicy#ackTimeout()
         */
        KeepAliveConfigurator ackTimeout(Duration ackTimeout);

        /**
         * Allow/disallow sending or receiving <a href="https://tools.ietf.org/html/rfc7540#page-42">pings</a> even when
         * no streams are <a href="https://tools.ietf.org/html/rfc7540#page-16">active</a>.
         *
         * @param withoutActiveStreams {@code true} if <a href="https://tools.ietf.org/html/rfc7540#page-42">pings</a>
         * are expected when no streams are <a href="https://tools.ietf.org/html/rfc7540#page-16">active</a>.
         * @return {@code this}.
         * @see KeepAlivePolicy#withoutActiveStreams()
         */
        KeepAliveConfigurator withoutActiveStreams(boolean withoutActiveStreams);

        /**
         * Disables sending of <a href="https://tools.ietf.org/html/rfc7540#page-42">pings</a>.
         *
         * @return {@code this}.
         */
        KeepAliveConfigurator disable();

        /**
         * Commits all changes made to this {@link KeepAliveConfigurator} to the {@link H2ProtocolConfigBuilder} that
         * created this {@link KeepAliveConfigurator}.
         *
         * @return The {@link H2ProtocolConfigBuilder} that created this {@link KeepAliveConfigurator}.
         */
        H2ProtocolConfigBuilder commit();
    }

    private static final class DefaultH2ProtocolConfig implements H2ProtocolConfig {

        private final HttpHeadersFactory headersFactory;
        private final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector;
        @Nullable
        private final String frameLoggerName;
        @Nullable
        private final KeepAlivePolicy keepAlivePolicy;

        DefaultH2ProtocolConfig(final HttpHeadersFactory headersFactory,
                                final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector,
                                @Nullable final String frameLogger, @Nullable final KeepAlivePolicy keepAlivePolicy) {
            this.headersFactory = headersFactory;
            this.headersSensitivityDetector = headersSensitivityDetector;
            this.frameLoggerName = frameLogger;
            this.keepAlivePolicy = keepAlivePolicy;
        }

        @Override
        public HttpHeadersFactory headersFactory() {
            return headersFactory;
        }

        @Override
        public BiPredicate<CharSequence, CharSequence> headersSensitivityDetector() {
            return headersSensitivityDetector;
        }

        @Nullable
        @Override
        public String frameLoggerName() {
            return frameLoggerName;
        }

        @Nullable
        @Override
        public KeepAlivePolicy keepAlivePolicy() {
            return keepAlivePolicy;
        }
   }
}
