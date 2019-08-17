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

import io.servicetalk.http.api.HttpHeadersFactory;

import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.H2ToStH1Utils.DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS;
import static java.util.Objects.requireNonNull;

final class H2ServerConfig {
    private HttpHeadersFactory h2HeadersFactory;
    private BiPredicate<CharSequence, CharSequence> h2HeadersSensitivityDetector;
    @Nullable
    private String h2FrameLogger;
    private int gracefulShutdownTimeoutMs;

    H2ServerConfig() {
        h2HeadersFactory = H2HeadersFactory.INSTANCE;
        h2HeadersSensitivityDetector = H2HeadersFactory.DEFAULT_SENSITIVITY_DETECTOR;
        gracefulShutdownTimeoutMs = DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS;
    }

    HttpHeadersFactory h2HeadersFactory() {
        return h2HeadersFactory;
    }

    void h2HeadersFactory(final HttpHeadersFactory headersFactory) {
        this.h2HeadersFactory = requireNonNull(headersFactory);
    }

    BiPredicate<CharSequence, CharSequence> h2HeadersSensitivityDetector() {
        return h2HeadersSensitivityDetector;
    }

    void h2HeadersSensitivityDetector(final BiPredicate<CharSequence, CharSequence> h2HeadersSensitivityDetector) {
        this.h2HeadersSensitivityDetector = requireNonNull(h2HeadersSensitivityDetector);
    }

    @Nullable
    String h2FrameLogger() {
        return h2FrameLogger;
    }

    void h2FrameLogger(@Nullable String h2FrameLogger) {
        this.h2FrameLogger = h2FrameLogger;
    }

    int gracefulShutdownTimeoutMs() {
        return gracefulShutdownTimeoutMs;
    }

    void gracefulShutdownTimeoutMs(int gracefulShutdownTimeoutMs) {
        this.gracefulShutdownTimeoutMs = gracefulShutdownTimeoutMs;
    }

    /**
     * Returns an immutable view of this config, any changes to this config will not alter the returned view.
     *
     * @return a read only version of this object.
     */
    ReadOnlyH2ServerConfig asReadOnly() {
        return new ReadOnlyH2ServerConfig(this);
    }
}
