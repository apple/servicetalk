/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry.http;

import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.transport.api.HostAndPort;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

import static io.servicetalk.http.api.HttpHeaderNames.USER_AGENT;

final class RequestTagExtractor {

    private RequestTagExtractor() {
        // empty private constructor
    }

    private static String getRequestMethod(HttpRequestMetaData req) {
        return req.method().name();
    }

    private static String getHttpUrl(HttpRequestMetaData req) {
        return req.path()
            + (req.rawQuery() == null ? "" : '?' + req.rawQuery());
    }

    static Span reportTagsAndStart(SpanBuilder span, HttpRequestMetaData httpRequestMetaData) {
        span.setAttribute("http.url", getHttpUrl(httpRequestMetaData));
        span.setAttribute("http.method", getRequestMethod(httpRequestMetaData));
        span.setAttribute("http.target", getHttpUrl(httpRequestMetaData));
        span.setAttribute("http.route", httpRequestMetaData.rawPath());
        span.setAttribute("http.flavor", getFlavor(httpRequestMetaData.version()));
        CharSequence userAgent = httpRequestMetaData.headers().get(USER_AGENT);
        if (userAgent != null) {
            span.setAttribute("http.user_agent", userAgent.toString());
        }
        String scheme = httpRequestMetaData.scheme();
        if (scheme != null) {
            span.setAttribute("http.scheme", scheme);
        }
        HostAndPort hostAndPort = httpRequestMetaData.effectiveHostAndPort();
        if (hostAndPort != null) {
            span.setAttribute("net.host.name", hostAndPort.hostName());
            span.setAttribute("net.host.port", hostAndPort.port());
        }
        return span.startSpan();
    }

    private static String getFlavor(final HttpProtocolVersion version) {
        if (version.major() == 1) {
            if (version.minor() == 1) {
                return "1.1";
            }
            if (version.minor() == 0) {
                return "1.0";
            }
        } else if (version.major() == 2 && version.minor() == 0) {
            return "2.0";
        }
        return version.major() + "." + version.minor();
    }
}
