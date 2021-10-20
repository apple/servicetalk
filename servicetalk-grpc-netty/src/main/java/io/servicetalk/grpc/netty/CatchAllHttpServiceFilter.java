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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.api.MessageEncodingException;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.serializer.api.SerializationException;

import com.google.rpc.Status;

import java.util.Base64;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.api.GrpcStatusCode.CANCELLED;
import static io.servicetalk.grpc.api.GrpcStatusCode.DEADLINE_EXCEEDED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.grpc.api.GrpcStatusCode.fromHttp2ErrorCode;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_CONTENT_TYPE;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_STATUS_CODE_TRAILER;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_STATUS_DETAILS_TRAILER;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_STATUS_MESSAGE_TRAILER;
import static io.servicetalk.grpc.internal.GrpcConstants.GRPC_USER_AGENT;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.SERVER;
import static java.lang.String.valueOf;

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
            final HttpServiceContext ctx, final StreamingHttpResponseFactory responseFactory,
            final Throwable cause) {
        final StreamingHttpResponse response = responseFactory.ok();
        final HttpHeaders headers = response.headers();
        headers.set(SERVER, GRPC_USER_AGENT);
        headers.set(CONTENT_TYPE, GRPC_CONTENT_TYPE);

        if (cause instanceof GrpcStatusException) {
            GrpcStatusException grpcStatusException = (GrpcStatusException) cause;
            setStatus(response.headers(), grpcStatusException.status(),
                    grpcStatusException.applicationStatus(), ctx.executionContext().bufferAllocator());
        } else {
            setStatus(response.headers(), toGrpcStatus(cause), null, ctx.executionContext().bufferAllocator());
        }

        return response;
    }

    static GrpcStatus toGrpcStatus(Throwable cause) {
        final GrpcStatus status;
        if (cause instanceof Http2Exception) {
            Http2Exception h2Exception = (Http2Exception) cause;
            status = new GrpcStatus(fromHttp2ErrorCode(h2Exception.errorCode()), cause);
        } else if (cause instanceof MessageEncodingException) {
            MessageEncodingException msgEncException = (MessageEncodingException) cause;
            status = new GrpcStatus(UNIMPLEMENTED, cause, "Message encoding '" + msgEncException.encoding()
                    + "' not supported ");
        } else if (cause instanceof SerializationException) {
            status = new GrpcStatus(UNKNOWN, cause, "Serialization error: " + cause.getMessage());
        } else if (cause instanceof CancellationException) {
            status = new GrpcStatus(CANCELLED, cause);
        } else if (cause instanceof TimeoutException) {
            status = new GrpcStatus(DEADLINE_EXCEEDED, cause);
        } else {
            // Initialize detail because cause is often lost
            status = new GrpcStatus(UNKNOWN, cause, cause.toString());
        }

        return status;
    }

    static void setStatus(final HttpHeaders trailers, final GrpcStatus status, @Nullable final Status details,
                          @Nullable final BufferAllocator allocator) {
        trailers.set(GRPC_STATUS_CODE_TRAILER, valueOf(status.code().value()));
        if (status.description() != null) {
            trailers.set(GRPC_STATUS_MESSAGE_TRAILER, status.description());
        }
        if (details != null) {
            assert allocator != null;
            trailers.set(GRPC_STATUS_DETAILS_TRAILER,
                    newAsciiString(allocator.wrap(Base64.getEncoder().encode(details.toByteArray()))));
        }
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        return strategy;    // no influence since we do not block
    }
}
