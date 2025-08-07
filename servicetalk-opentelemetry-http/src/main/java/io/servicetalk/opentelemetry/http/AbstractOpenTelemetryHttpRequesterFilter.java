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

import io.opentelemetry.context.Context;

import javax.annotation.Nullable;

abstract class AbstractOpenTelemetryHttpRequesterFilter extends AbstractOpenTelemetryFilter
        implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

    private final HttpInstrumentationHelper httpHelper;
    private final GrpcInstrumentationHelper grpcHelper;

    /**
     * Create a new instance.
     *
     * @param opentelemetryOptions extra options to create the opentelemetry filter, including OpenTelemetry
     *                            instance and componentName
     */
    AbstractOpenTelemetryHttpRequesterFilter(final OpenTelemetryOptions opentelemetryOptions) {
        this.httpHelper = HttpInstrumentationHelper.createClient(opentelemetryOptions);
        this.grpcHelper = GrpcInstrumentationHelper.createClient(opentelemetryOptions);
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

    private Single<StreamingHttpResponse> trackRequest(
            final StreamingHttpRequester delegate,
            final StreamingHttpRequest request,
            @Nullable ConnectionInfo connectionInfo) {
        // This should be (essentially) the first filter in the client filter chain, so here is where we initialize
        // all the little bits and pieces that will end up being part of the request context. This includes setting
        // up the retry counter context entry since retries will happen further down in the filter chain.
        InstrumentationHelper helper = grpcHelper.isGrpcRequest(request) ? grpcHelper : httpHelper;
        return helper.trackRequest(delegate::request, new RequestInfo(request, connectionInfo),
                Context.current());
    }
}
