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
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;

import javax.annotation.Nullable;

abstract class AbstractOpenTelemetryHttpRequesterFilter extends AbstractOpenTelemetryFilter
        implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

    // package private for testing
    static final ContextKey<Boolean> SHOULD_INSTRUMENT_KEY = ContextKey.named("should-instrument-carrier");

    private final HttpInstrumentationHelper httpHelper;
    private final GrpcInstrumentationHelper grpcHelper;

    /**
     * Create a new instance.
     *
     * @param builder options to create the opentelemetry filter, including OpenTelemetry instance and componentName
     */
    AbstractOpenTelemetryHttpRequesterFilter(final OpenTelemetryHttpRequesterFilter.Builder builder) {
        this.httpHelper = HttpInstrumentationHelper.forClient(builder);
        this.grpcHelper = GrpcInstrumentationHelper.forClient(builder);
    }

    @Override
    public StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return Single.defer(() -> trackRequest(delegate, request, null, false).shareContextOnSubscribe());
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return Single.defer(() -> trackRequest(delegate(), request,
                        connectionContext(), true).shareContextOnSubscribe());
            }
        };
    }

    private Single<StreamingHttpResponse> trackRequest(
            final StreamingHttpRequester delegate,
            final StreamingHttpRequest request,
            @Nullable ConnectionInfo connectionInfo,
            boolean connectionLevel) {
        InstrumentationHelper helper = grpcHelper.isGrpcRequest(request) ? grpcHelper : httpHelper;
        RequestInfo requestInfo = new RequestInfo(request, connectionInfo);
        Context current = Context.current();
        if (connectionLevel) {
            Boolean shouldStartKey = current.get(SHOULD_INSTRUMENT_KEY);
            // If we're a connection level filter we want to first see if a higher level filter has already
            // decided whether to start or not and go with that. If there is no context key, we are the only
            // filter, and we should make the decision ourselves.
            boolean shouldStart = shouldStartKey != null ? shouldStartKey : helper.shouldStart(current, requestInfo);
            return shouldStart ? helper.trackRequest(delegate::request, requestInfo, current) :
                    delegate.request(requestInfo.request());
        } else {
            boolean shouldStart = helper.shouldStart(current, requestInfo);
            current = current.with(SHOULD_INSTRUMENT_KEY, shouldStart);
            try (Scope ignored = current.makeCurrent()) {
                return shouldStart ? helper.trackRequest(delegate::request, requestInfo, current) :
                        delegate.request(requestInfo.request());
            }
        }
    }
}
