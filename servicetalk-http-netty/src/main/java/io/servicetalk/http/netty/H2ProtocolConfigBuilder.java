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
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.logging.api.UserDataLoggerConfig;
import io.servicetalk.logging.slf4j.internal.DefaultUserDataLoggerConfig;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.H2HeadersFactory.DEFAULT_SENSITIVITY_DETECTOR;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.DISABLE_KEEP_ALIVE;
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
    private UserDataLoggerConfig frameLoggerConfig;
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
        this.keepAlivePolicy = policy == DISABLE_KEEP_ALIVE ? null : requireNonNull(policy);
        return this;
    }

    /**
     * Builds {@link H2ProtocolConfig}.
     *
     * @return {@link H2ProtocolConfig}
     */
    public H2ProtocolConfig build() {
        return new DefaultH2ProtocolConfig(headersFactory, headersSensitivityDetector, frameLoggerConfig,
                keepAlivePolicy);
    }

    private static final class DefaultH2ProtocolConfig implements H2ProtocolConfig {

        private final HttpHeadersFactory headersFactory;
        private final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector;
        @Nullable
        private final UserDataLoggerConfig frameLoggerConfig;
        @Nullable
        private final KeepAlivePolicy keepAlivePolicy;

        DefaultH2ProtocolConfig(final HttpHeadersFactory headersFactory,
                                final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector,
                                @Nullable final UserDataLoggerConfig frameLoggerConfig,
                                @Nullable final KeepAlivePolicy keepAlivePolicy) {
            this.headersFactory = headersFactory;
            this.headersSensitivityDetector = headersSensitivityDetector;
            this.frameLoggerConfig = frameLoggerConfig;
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
        public UserDataLoggerConfig frameLoggerConfig() {
            return frameLoggerConfig;
        }

        @Nullable
        @Override
        public KeepAlivePolicy keepAlivePolicy() {
            return keepAlivePolicy;
        }
    }
}
