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

final class ReadOnlyH2ServerConfig {
    private final HttpHeadersFactory h2HeadersFactory;
    private final BiPredicate<CharSequence, CharSequence> h2HeadersSensitivityDetector;
    @Nullable
    private final String h2FrameLogger;
    private final int gracefulShutdownTimeoutMs;

    ReadOnlyH2ServerConfig(H2ServerConfig rhs) {
        h2HeadersFactory = rhs.h2HeadersFactory();
        h2HeadersSensitivityDetector = rhs.h2HeadersSensitivityDetector();
        h2FrameLogger = rhs.h2FrameLogger();
        gracefulShutdownTimeoutMs = rhs.gracefulShutdownTimeoutMs();
    }

    HttpHeadersFactory h2HeadersFactory() {
        return h2HeadersFactory;
    }

    BiPredicate<CharSequence, CharSequence> h2HeadersSensitivityDetector() {
        return h2HeadersSensitivityDetector;
    }

    @Nullable
    String h2FrameLogger() {
        return h2FrameLogger;
    }

    int gracefulShutdownTimeoutMs() {
        return gracefulShutdownTimeoutMs;
    }
}
