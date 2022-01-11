/*
 * Copyright © 2021-2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcUtils.GrpcStatusUpdater;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcHeaderNames.GRPC_STATUS;
import static io.servicetalk.grpc.api.GrpcHeaderValues.APPLICATION_GRPC;
import static io.servicetalk.grpc.api.GrpcUtils.STATUS_OK;
import static io.servicetalk.grpc.api.GrpcUtils.newErrorResponse;

/**
 * Filter that maps known {@link Throwable} subtypes into an gRPC response with an appropriate {@link GrpcStatusCode}.
 * <p>
 * This filter is recommended to be placed as early as possible to make sure it captures all exceptions that may be
 * generated by other filters.
 */
public final class GrpcExceptionMapperServiceFilter implements StreamingHttpServiceFilterFactory {

    /**
     * Instance of {@link GrpcExceptionMapperServiceFilter}.
     */
    public static final StreamingHttpServiceFilterFactory INSTANCE = new GrpcExceptionMapperServiceFilter();

    private GrpcExceptionMapperServiceFilter() {
        // Singleton
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {

            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                final BufferAllocator allocator = ctx.executionContext().bufferAllocator();
                final Single<StreamingHttpResponse> handle;
                try {
                    handle = delegate().handle(ctx, request, responseFactory).map(response -> {
                        // Some responses may already have a grpc-status in headers, see GrpcRouter
                        if (response.headers().contains(GRPC_STATUS)) {
                            return response;
                        }
                        return response.transform(new GrpcStatusUpdater(allocator, STATUS_OK));
                    });

                } catch (Throwable cause) {
                    return succeeded(convertToGrpcErrorResponse(responseFactory, cause, allocator));
                }
                return handle.onErrorReturn(cause -> convertToGrpcErrorResponse(responseFactory, cause, allocator));
            }
        };
    }

    private static StreamingHttpResponse convertToGrpcErrorResponse(final StreamingHttpResponseFactory responseFactory,
                                                                    final Throwable cause,
                                                                    final BufferAllocator allocator) {
        return newErrorResponse(responseFactory, APPLICATION_GRPC, cause, allocator);
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
    }
}
