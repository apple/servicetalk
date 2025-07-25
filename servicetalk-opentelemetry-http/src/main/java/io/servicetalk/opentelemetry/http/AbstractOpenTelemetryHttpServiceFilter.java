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
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import io.opentelemetry.api.OpenTelemetry;

abstract class AbstractOpenTelemetryHttpServiceFilter extends AbstractOpenTelemetryFilter
        implements StreamingHttpServiceFilterFactory {
    private final HttpInstrumentationHelper httpHelper;
    private final GrpcInstrumentationHelper grpcHelper;

    AbstractOpenTelemetryHttpServiceFilter(final OpenTelemetry openTelemetry,
                                           final OpenTelemetryOptions openTelemetryOptions) {
        // Create HTTP instrumentation helper
        this.httpHelper = HttpInstrumentationHelper.createServer(openTelemetry, openTelemetryOptions);

        // Create gRPC instrumentation helper only if gRPC support is available
        this.grpcHelper = GrpcInstrumentationHelper.createServer(openTelemetry, openTelemetryOptions);
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return trackRequest(delegate(), ctx, request, responseFactory);
            }
        };
    }

    private Single<StreamingHttpResponse> trackRequest(final StreamingHttpService delegate,
                                                       final HttpServiceContext ctx,
                                                       final StreamingHttpRequest request,
                                                       final StreamingHttpResponseFactory responseFactory) {
        // Detect protocol and delegate to appropriate instrumentation helper
        if (grpcHelper.isGrpcRequest(request)) {
            // Handle as gRPC request
            return grpcHelper.trackGrpcRequest(
                    req -> delegate.handle(ctx, req, responseFactory),
                    request,
                    ctx);
        } else {
            // Handle as HTTP request
            return httpHelper.trackHttpRequest(
                    req -> delegate.handle(ctx, req, responseFactory),
                    request,
                    ctx);
        }
    }
}
