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

import io.opentelemetry.context.Context;

abstract class AbstractOpenTelemetryHttpServiceFilter extends AbstractOpenTelemetryFilter
        implements StreamingHttpServiceFilterFactory {
    private final HttpInstrumentationHelper httpHelper;
    private final GrpcInstrumentationHelper grpcHelper;

    AbstractOpenTelemetryHttpServiceFilter(final OpenTelemetryHttpServiceFilter.Builder builder) {
        this.httpHelper = HttpInstrumentationHelper.forServer(builder);
        this.grpcHelper = GrpcInstrumentationHelper.forServer(builder);
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
        InstrumentationHelper helper = grpcHelper.isGrpcRequest(request) ? grpcHelper : httpHelper;
        Context current = Context.current();
        RequestInfo requestInfo = new RequestInfo(request, ctx);
        if (!helper.shouldStart(current, requestInfo)) {
            return delegate.handle(ctx, request, responseFactory);
        } else {
            return helper.trackRequest(
                req -> delegate.handle(ctx, req, responseFactory),
                new RequestInfo(request, ctx),
                current);
        }
    }
}
