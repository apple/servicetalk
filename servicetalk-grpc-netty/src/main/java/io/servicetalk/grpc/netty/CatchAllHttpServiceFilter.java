/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcHeaderValues.APPLICATION_GRPC;
import static io.servicetalk.grpc.netty.GrpcUtils.newErrorResponse;

final class CatchAllHttpServiceFilter implements StreamingHttpServiceFilterFactory,
                                                 HttpExecutionStrategyInfluencer {

    public static final StreamingHttpServiceFilterFactory INSTANCE = new CatchAllHttpServiceFilter();

    private CatchAllHttpServiceFilter() {
        // Singleton
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {

            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                final Single<StreamingHttpResponse> handle;
                try {
                    handle = delegate().handle(ctx, request, responseFactory);
                } catch (Throwable cause) {
                    return succeeded(convertToGrpcErrorResponse(ctx, responseFactory, cause));
                }
                return handle.onErrorReturn(cause -> convertToGrpcErrorResponse(ctx, responseFactory, cause));
            }
        };
    }

    private static StreamingHttpResponse convertToGrpcErrorResponse(
            final HttpServiceContext ctx, final StreamingHttpResponseFactory responseFactory, final Throwable cause) {
        return newErrorResponse(responseFactory, APPLICATION_GRPC, cause, ctx.executionContext().bufferAllocator());
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        return strategy;    // no influence since we do not block
    }
}
