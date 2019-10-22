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
import io.servicetalk.transport.api.ProtocolConfig;

import org.slf4j.event.Level;

import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.H2HeadersFactory.DEFAULT_SENSITIVITY_DETECTOR;
import static java.util.Objects.requireNonNull;

/**
 * Builder for {@link DefaultH2ProtocolConfig}.
 */
public final class H2ProtocolConfigBuilder {

    private HttpHeadersFactory headersFactory = H2HeadersFactory.INSTANCE;
    private BiPredicate<CharSequence, CharSequence> headersSensitivityDetector = DEFAULT_SENSITIVITY_DETECTOR;
    @Nullable
    private String frameLoggerName;

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
     * Builds {@link ProtocolConfig}.
     *
     * @return {@link ProtocolConfig}
     */
    public H2ProtocolConfig build() {
        return new DefaultH2ProtocolConfig(headersFactory, headersSensitivityDetector, frameLoggerName);
    }

    private static final class DefaultH2ProtocolConfig implements H2ProtocolConfig {

        private final HttpHeadersFactory headersFactory;
        private final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector;
        @Nullable
        private final String frameLoggerName;

        DefaultH2ProtocolConfig(final HttpHeadersFactory headersFactory,
                                final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector,
                                @Nullable final String frameLogger) {
            this.headersFactory = headersFactory;
            this.headersSensitivityDetector = headersSensitivityDetector;
            this.frameLoggerName = frameLogger;
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
    }
}
