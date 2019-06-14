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

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

class ReadOnlyH2ClientConfig {
    private static final int DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS = 30000;
    private HttpHeadersFactory h2HeadersFactory;
    @Nullable
    private String h2FrameLogger;
    private int gracefulShutdownTimeoutMs;

    ReadOnlyH2ClientConfig() {
        h2HeadersFactory = H2HeadersFactory.INSTANCE;
        gracefulShutdownTimeoutMs = DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS;
    }

    ReadOnlyH2ClientConfig(ReadOnlyH2ClientConfig rhs) {
        h2HeadersFactory = rhs.h2HeadersFactory;
        h2FrameLogger = rhs.h2FrameLogger;
        gracefulShutdownTimeoutMs = rhs.gracefulShutdownTimeoutMs;
    }

    HttpHeadersFactory h2HeadersFactory() {
        return h2HeadersFactory;
    }

    void h2HeadersFactory(final HttpHeadersFactory headersFactory) {
        this.h2HeadersFactory = requireNonNull(headersFactory);
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
}
