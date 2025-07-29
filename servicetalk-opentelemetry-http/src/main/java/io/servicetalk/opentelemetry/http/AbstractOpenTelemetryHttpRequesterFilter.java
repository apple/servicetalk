/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionInfo;

import io.opentelemetry.api.OpenTelemetry;

import javax.annotation.Nullable;

abstract class AbstractOpenTelemetryHttpRequesterFilter extends AbstractOpenTelemetryFilter
        implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

    private final HttpInstrumentationHelper httpHelper;

    /**
     * Create a new instance.
     *
     * @param openTelemetry        the {@link OpenTelemetry}.
     * @param componentName        The component name used during building new spans.
     * @param opentelemetryOptions extra options to create the opentelemetry filter.
     */
    AbstractOpenTelemetryHttpRequesterFilter(final OpenTelemetry openTelemetry, String componentName,
                                             final OpenTelemetryOptions opentelemetryOptions) {
        // Create HTTP instrumentation helper
        this.httpHelper = HttpInstrumentationHelper.createClient(openTelemetry, opentelemetryOptions, componentName);
    }

    @Override
    public StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return Single.defer(() -> trackRequest(delegate, request, null).shareContextOnSubscribe());
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return Single.defer(() -> trackRequest(delegate(), request,
                        connectionContext()).shareContextOnSubscribe());
            }
        };
    }

    private Single<StreamingHttpResponse> trackRequest(final StreamingHttpRequester delegate,
                                                       final StreamingHttpRequest request,
                                                       @Nullable ConnectionInfo connectionInfo) {
        // This should be (essentially) the first filter in the client filter chain, so here is where we initialize
        // all the little bits and pieces that will end up being part of the request context. This includes setting
        // up the retry counter context entry since retries will happen further down in the filter chain.
        return httpHelper.trackHttpRequest(
                delegate::request,
                request,
                connectionInfo);
    }
}
