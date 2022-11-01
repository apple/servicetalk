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

package io.servicetalk.opentelemetry;

import io.servicetalk.http.api.HttpRequestMetaData;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

final class RequestTagExtractor {

    static final RequestTagExtractor INSTANCE = new RequestTagExtractor();

    private RequestTagExtractor() {
        // empty private constructor
    }

    private String getRequestMethod(HttpRequestMetaData req) {
        return req.method().name();
    }

    private String getHttpUrl(HttpRequestMetaData req) {
        return (req.scheme() == null ? "http" : req.scheme()) + "://" +
            (req.effectiveHostAndPort() == null ? "localhost:8080" : req.effectiveHostAndPort())
            + req.rawPath()
            + (req.rawQuery() == null ? "" : "?" + req.rawQuery());
    }

    static Span reportTagsAndStart(SpanBuilder span, HttpRequestMetaData httpRequestMetaData) {
        final RequestTagExtractor tagExtractor = RequestTagExtractor.INSTANCE;
        span.setAttribute("http.url", tagExtractor.getHttpUrl(httpRequestMetaData));
        span.setAttribute("http.method", tagExtractor.getRequestMethod(httpRequestMetaData));
        return span.startSpan();
    }
}
