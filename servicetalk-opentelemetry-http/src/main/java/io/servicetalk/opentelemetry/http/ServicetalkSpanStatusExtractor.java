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

    static final ServicetalkSpanStatusExtractor INSTANCE = new ServicetalkSpanStatusExtractor();

    private ServicetalkSpanStatusExtractor() {
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
            switch (status.status().statusClass()) {
                case CLIENT_ERROR_4XX:
                case SERVER_ERROR_5XX:
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                    break;
                default:
                    spanStatusBuilder.setStatus(StatusCode.OK);
                    break;
            }
        } else {
            SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, null, null);
        }
    }
}
