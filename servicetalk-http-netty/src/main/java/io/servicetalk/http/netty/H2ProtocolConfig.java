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

/**
 * Configuration for <a href="https://tools.ietf.org/html/rfc7540">HTTP/2</a> protocol.
 */
public interface H2ProtocolConfig extends ProtocolConfig {

    @Override
    default String alpnId() {
        return AlpnIds.HTTP_2;
    }

    /**
     * {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding HTTP messages.
     *
     * @return {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding HTTP messages
     */
    HttpHeadersFactory headersFactory();

    /**
     * Sensitivity detector to determine if a header {@code name}/{@code value} pair should be treated as
     * <a href="https://tools.ietf.org/html/rfc7541#section-7.1.3">sensitive</a>.
     *
     * @return {@link BiPredicate}&lt;{@link CharSequence}, {@link CharSequence}&gt; that
     * returns {@code true} if a header &lt;{@code name}, {@code value}&gt; pair should be treated as
     * <a href="https://tools.ietf.org/html/rfc7541#section-7.1.3">sensitive</a>, {@code false} otherwise
     */
    BiPredicate<CharSequence, CharSequence> headersSensitivityDetector();

    /**
     * Logger name for HTTP/2 frames.
     * <p>
     * All frames will be logged at {@link Level#TRACE TRACE} level.
     *
     * @return the name of the logger to log HTTP/2 frames or {@code null} to disable it
     */
    @Nullable
    String frameLoggerName();
}
