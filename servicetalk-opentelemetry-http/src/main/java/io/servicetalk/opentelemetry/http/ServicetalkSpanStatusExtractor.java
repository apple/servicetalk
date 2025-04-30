/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;

import javax.annotation.Nullable;

final class ServicetalkSpanStatusExtractor implements SpanStatusExtractor<HttpRequestMetaData, HttpResponseMetaData> {

    static final ServicetalkSpanStatusExtractor CLIENT_INSTANCE = new ServicetalkSpanStatusExtractor(true);
    static final ServicetalkSpanStatusExtractor SERVER_INSTANCE = new ServicetalkSpanStatusExtractor(false);

    private final boolean isClient;

    private ServicetalkSpanStatusExtractor(final boolean isClient) {
        this.isClient = isClient;
    }

    @Override
    public void extract(
            SpanStatusBuilder spanStatusBuilder,
            HttpRequestMetaData request,
            @Nullable HttpResponseMetaData status,
            @Nullable Throwable error) {
        if (error != null) {
            spanStatusBuilder.setStatus(StatusCode.ERROR);
        } else if (status != null) {
            // See https://opentelemetry.io/docs/specs/semconv/http/http-spans/#status for the conventions.
            switch (status.status().statusClass()) {
                case INFORMATIONAL_1XX:
                case SUCCESSFUL_2XX:
                case REDIRECTION_3XX:
                    // "Span Status MUST be left unset if HTTP status code was in the 1xx, 2xx or 3xx ranges, unless
                    // there was another error (e.g., network error receiving the response body; or 3xx codes with max
                    // redirects exceeded), in which case status MUST be set to Error."
                    break;
                case CLIENT_ERROR_4XX:
                    // "For HTTP status codes in the 4xx range span status MUST be left unset in case of
                    // SpanKind.SERVER and SHOULD be set to Error in case of SpanKind.CLIENT."
                    if (isClient) {
                        spanStatusBuilder.setStatus(StatusCode.ERROR);
                    }
                    break;
                case SERVER_ERROR_5XX:
                    // "For HTTP status codes in the 5xx range, as well as any other code the client failed to
                    // interpret, span status SHOULD be set to Error."
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                    break;
                default:
                    // Unknown to ServiceTalk. The client or server may know what it means, so we leave it unset.
                    break;
            }
        } else {
            SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, null, null);
        }
    }
}
