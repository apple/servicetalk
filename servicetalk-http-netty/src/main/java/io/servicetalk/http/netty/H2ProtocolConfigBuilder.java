/*
 * Copyright © 2019-2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.Http2Settings;
import io.servicetalk.http.api.Http2SettingsBuilder;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.netty.H2ProtocolConfig.KeepAlivePolicy;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.logging.api.UserDataLoggerConfig;
import io.servicetalk.logging.slf4j.internal.DefaultUserDataLoggerConfig;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.disabled;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.validateKeepAlivePolicy;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.util.Objects.requireNonNull;

/**
 * Builder for {@link H2ProtocolConfig}.
 *
 * @see HttpProtocolConfigs#h2()
 */
public final class H2ProtocolConfigBuilder {
    private static final BiPredicate<CharSequence, CharSequence> DEFAULT_SENSITIVITY_DETECTOR = (name, value) -> false;
    /**
     * 1mb default window size.
     */
    private static final int INITIAL_FLOW_CONTROL_WINDOW = 1_048_576;
    /**
     * Netty currently doubles the connection window by default so a single stream doesn't exhaust all flow control
     * bytes.
     */
    private static final int CONNECTION_STREAM_FLOW_CONTROL_INCREMENT = 0;
    /**
     * Default allocation quantum to use for the remote flow controller.
     */
    private static final int DEFAULT_FLOW_CONTROL_QUANTUM = 1024 * 16;
    private Http2Settings h2Settings = new Http2SettingsBuilder()
            .initialWindowSize(INITIAL_FLOW_CONTROL_WINDOW)
            .maxHeaderListSize(DEFAULT_HEADER_LIST_SIZE)
            .build();
    private HttpHeadersFactory headersFactory = H2HeadersFactory.INSTANCE;
    private BiPredicate<CharSequence, CharSequence> headersSensitivityDetector = DEFAULT_SENSITIVITY_DETECTOR;
    @Nullable
    private UserDataLoggerConfig frameLoggerConfig;
    private KeepAlivePolicy keepAlivePolicy = disabled();
    private int flowControlQuantum = DEFAULT_FLOW_CONTROL_QUANTUM;
    private int flowControlIncrement = CONNECTION_STREAM_FLOW_CONTROL_INCREMENT;

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
     *
     * @param loggerName provides the logger to log HTTP/2 frames.
     * @param logLevel the level to log HTTP/2 frames.
     * @param logUserData {@code true} to include user data (e.g. data, headers, etc.). {@code false} to exclude user
     * data and log only network events. This method is invoked for each data object allowing for dynamic behavior.
     * @return {@code this}
     */
    public H2ProtocolConfigBuilder enableFrameLogging(final String loggerName,
                                                      final LogLevel logLevel,
                                                      final BooleanSupplier logUserData) {
        frameLoggerConfig = new DefaultUserDataLoggerConfig(loggerName, logLevel, logUserData);
        return this;
    }

    /**
     * Sets the {@link KeepAlivePolicy} to use.
     *
     * @param policy {@link KeepAlivePolicy} to use.
     * @return {@code this}
     * @see H2KeepAlivePolicies
     */
    public H2ProtocolConfigBuilder keepAlivePolicy(final KeepAlivePolicy policy) {
        // Run validation in case someone passes a custom implementation of KeepAlivePolicy
        this.keepAlivePolicy = validateKeepAlivePolicy(policy);
        return this;
    }

    /**
     * Sets the initial <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-6.5.1">HTTP/2 Setting</a> to for
     * each h2 connection.
     * @param settings the initial settings to for each h2 connection.
     * @return {@code this}
     * @see Http2SettingsBuilder
     */
    public H2ProtocolConfigBuilder initialSettings(Http2Settings settings) {
        this.h2Settings = requireNonNull(settings);
        return this;
    }

    /**
     * Provide a hint on the number of bytes that the flow controller will attempt to give to a stream for each
     * allocation (assuming the stream has this much eligible data).
     * @param flowControlQuantum a hint on the number of bytes that the flow controller will attempt to give to a
     * stream for each allocation (assuming the stream has this much eligible data).
     * @return {@code this}
     */
    public H2ProtocolConfigBuilder flowControlQuantum(int flowControlQuantum) {
        this.flowControlQuantum = ensurePositive(flowControlQuantum, "flowControlQuantum");
        return this;
    }

    /**
     * Increment to apply to {@link Http2Settings#initialWindowSize()} for the connection stream. This expands the
     * connection flow control window so a single stream can't consume all the flow control credits.
     * @param connectionWindowIncrement The number of bytes to increment the local flow control window for the
     * connection stream.
     * @return {@code this}
     */
    public H2ProtocolConfigBuilder flowControlWindowIncrement(int connectionWindowIncrement) {
        this.flowControlIncrement = ensurePositive(connectionWindowIncrement, "connectionWindowIncrement");
        return this;
    }

    /**
     * Builds {@link H2ProtocolConfig}.
     *
     * @return {@link H2ProtocolConfig}
     */
    public H2ProtocolConfig build() {
        return new DefaultH2ProtocolConfig(h2Settings, headersFactory, headersSensitivityDetector, frameLoggerConfig,
                keepAlivePolicy, flowControlQuantum, flowControlIncrement);
    }

    private static final class DefaultH2ProtocolConfig implements H2ProtocolConfig {
        private final Http2Settings h2Settings;
        private final HttpHeadersFactory headersFactory;
        private final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector;
        @Nullable
        private final UserDataLoggerConfig frameLoggerConfig;
        private final KeepAlivePolicy keepAlivePolicy;
        private final int flowControlQuantum;
        private final int flowControlIncrement;

        DefaultH2ProtocolConfig(final Http2Settings h2Settings,
                                final HttpHeadersFactory headersFactory,
                                final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector,
                                @Nullable final UserDataLoggerConfig frameLoggerConfig,
                                final KeepAlivePolicy keepAlivePolicy,
                                final int flowControlQuantum,
                                final int flowControlIncrement) {
            this.h2Settings = h2Settings;
            this.headersFactory = headersFactory;
            this.headersSensitivityDetector = headersSensitivityDetector;
            this.frameLoggerConfig = frameLoggerConfig;
            this.keepAlivePolicy = keepAlivePolicy;
            this.flowControlQuantum = flowControlQuantum;
            this.flowControlIncrement = flowControlIncrement;
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
        public UserDataLoggerConfig frameLoggerConfig() {
            return frameLoggerConfig;
        }

        @Override
        public KeepAlivePolicy keepAlivePolicy() {
            return keepAlivePolicy;
        }

        @Override
        public Http2Settings initialSettings() {
            return h2Settings;
        }

        @Override
        public int flowControlQuantum() {
            return flowControlQuantum;
        }

        @Override
        public int flowControlWindowIncrement() {
            return flowControlIncrement;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "{alpnId=" + alpnId() +
                    ", headersFactory=" + headersFactory +
                    ", headersSensitivityDetector=" + (headersSensitivityDetector == DEFAULT_SENSITIVITY_DETECTOR ?
                    "DEFAULT_SENSITIVITY_DETECTOR" : headersSensitivityDetector.toString()) +
                    ", frameLoggerConfig=" + frameLoggerConfig +
                    ", keepAlivePolicy=" + keepAlivePolicy +
                    ", flowControlQuantum=" + flowControlQuantum +
                    ", flowControlIncrement=" + flowControlIncrement +
                    ", h2Settings=" + h2Settings + '}';
        }
    }
}
