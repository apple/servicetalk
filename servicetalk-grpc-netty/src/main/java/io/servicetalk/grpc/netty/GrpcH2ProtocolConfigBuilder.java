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
package io.servicetalk.grpc.netty;

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.netty.H2ProtocolConfig;
import io.servicetalk.http.netty.H2ProtocolConfigBuilder;
import io.servicetalk.http.netty.HttpProtocolConfigs;

import org.slf4j.event.Level;

import java.util.function.BiPredicate;
import javax.annotation.Nullable;

/**
 * Builder for {@link GrpcH2ProtocolConfig}.
 *
 * @see GrpcProtocolConfigs#h2()
 */
public final class GrpcH2ProtocolConfigBuilder {

    private H2ProtocolConfigBuilder h2Builder = HttpProtocolConfigs.h2();

    GrpcH2ProtocolConfigBuilder() {
    }

    /**
     * Sets the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding HTTP messages.
     *
     * @param headersFactory {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding HTTP
     * messages
     * @return {@code this}
     */
    public GrpcH2ProtocolConfigBuilder headersFactory(final HttpHeadersFactory headersFactory) {
        h2Builder.headersFactory(headersFactory);
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
    public GrpcH2ProtocolConfigBuilder headersSensitivityDetector(
            final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector) {
        h2Builder.headersSensitivityDetector(headersSensitivityDetector);
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
    public GrpcH2ProtocolConfigBuilder enableFrameLogging(final String loggerName) {
        h2Builder.enableFrameLogging(loggerName);
        return this;
    }

    /**
     * Builds {@link GrpcH2ProtocolConfig}.
     *
     * @return {@link GrpcH2ProtocolConfig}
     */
    public GrpcH2ProtocolConfig build() {
        return new DefaultGrpcH2ProtocolConfig(h2Builder.build());
    }

    private static final class DefaultGrpcH2ProtocolConfig implements GrpcH2ProtocolConfig {

        private final H2ProtocolConfig h2Config;

        DefaultGrpcH2ProtocolConfig(final H2ProtocolConfig h2Config) {
            this.h2Config = h2Config;
        }

        @Override
        public HttpHeadersFactory headersFactory() {
            return h2Config.headersFactory();
        }

        @Override
        public BiPredicate<CharSequence, CharSequence> headersSensitivityDetector() {
            return h2Config.headersSensitivityDetector();
        }

        @Nullable
        @Override
        public String frameLoggerName() {
            return h2Config.frameLoggerName();
        }
    }
}
