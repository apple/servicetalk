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

import io.servicetalk.http.api.StreamingHttpRequest;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

final class RequestTagExtractor implements TagExtractor<StreamingHttpRequest> {

    public static final TagExtractor<StreamingHttpRequest> INSTANCE = new RequestTagExtractor();

    private RequestTagExtractor() {
        // empty private constructor
    }

    public int len(StreamingHttpRequest req) {
        return 2;
    }

    public String name(StreamingHttpRequest req, int index) {
        switch (index) {
            case 0:
                return "http.url";
            case 1:
                return "http.method";
            default:
                throw new IndexOutOfBoundsException("Invalid tag index " + index);
        }
    }

    public String value(StreamingHttpRequest req, int index) {
        switch (index) {
            case 0:
                return (req.scheme() == null ? "http" : req.scheme()) + "://" +
                    (req.effectiveHostAndPort() == null ? "localhost:8080" : req.effectiveHostAndPort())
                    + req.rawPath()
                    + (req.rawQuery() == null ? "" : "?" + req.rawQuery());
            case 1:
                return req.method().name();
            default:
                throw new IndexOutOfBoundsException("Invalid tag index " + index);
        }
    }

    static Span reportTagsAndStart(SpanBuilder span, StreamingHttpRequest obj) {
        final TagExtractor<StreamingHttpRequest> tagExtractor = RequestTagExtractor.INSTANCE;
        int len = tagExtractor.len(obj);
        for (int idx = 0; idx < len; idx++) {
            span.setAttribute(tagExtractor.name(obj, idx), tagExtractor.value(obj, idx));
        }
        return span.startSpan();
    }
}
