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
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.api.MessageEncodingException;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.serializer.api.SerializationException;

import com.google.rpc.Status;

import java.util.Base64;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.grpc.api.GrpcHeaderNames.GRPC_MESSAGE_ACCEPT_ENCODING;
import static io.servicetalk.grpc.api.GrpcHeaderNames.GRPC_MESSAGE_ENCODING;
import static io.servicetalk.grpc.api.GrpcHeaderNames.GRPC_STATUS;
import static io.servicetalk.grpc.api.GrpcHeaderNames.GRPC_STATUS_DETAILS_BIN;
import static io.servicetalk.grpc.api.GrpcHeaderNames.GRPC_STATUS_MESSAGE;
import static io.servicetalk.grpc.api.GrpcHeaderValues.GRPC_USER_AGENT;
import static io.servicetalk.grpc.api.GrpcStatusCode.CANCELLED;
import static io.servicetalk.grpc.api.GrpcStatusCode.DEADLINE_EXCEEDED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.grpc.api.GrpcStatusCode.fromHttp2ErrorCode;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.SERVER;
import static java.lang.String.valueOf;

final class GrpcUtils {

    private GrpcUtils() {
        // No instances.
    }

    // This code is a copy of io.servicetalk.grpc.api.GrpcUtils.newErrorResponse
    // but cannot be shared because we don't have an internal module for grpc code that is both using the api module
    // and is also used in the api module as that would lead to circular dependency.
    static StreamingHttpResponse newErrorResponse(final StreamingHttpResponseFactory responseFactory,
                                                  final CharSequence contentType,
                                                  final Throwable cause,
                                                  final BufferAllocator allocator) {
        final StreamingHttpResponse response = responseFactory.ok();
        initResponse(response, contentType, null, null);
        setStatus(response.headers(), cause, allocator);
        return response;
    }

    // This code is a copy of io.servicetalk.grpc.api.GrpcUtils.initResponse
    // but cannot be shared because we don't have an internal module for grpc code that is both using the api module
    // and is also used in the api module as that would lead to circular dependency.
    static void initResponse(final HttpResponseMetaData response,
                             final CharSequence contentType,
                             @Nullable final CharSequence encoding,
                             @Nullable final CharSequence acceptedEncoding) {
        // The response status is 200 no matter what. Actual status is put in trailers.
        final HttpHeaders headers = response.headers();
        headers.set(SERVER, GRPC_USER_AGENT);
        headers.set(CONTENT_TYPE, contentType);
        if (encoding != null) {
            headers.set(GRPC_MESSAGE_ENCODING, encoding);
        }
        if (acceptedEncoding != null) {
            headers.set(GRPC_MESSAGE_ACCEPT_ENCODING, acceptedEncoding);
        }
    }

    // This code is a copy to io.servicetalk.grpc.api.GrpcUtils.setStatus
    // but cannot be shared because we don't have an internal module for grpc code that is both using the api module
    // and is also used in the api module as that would lead to circular dependency.
    static void setStatus(final HttpHeaders trailers, final Throwable cause, final BufferAllocator allocator) {
        if (cause instanceof GrpcStatusException) {
            GrpcStatusException grpcStatusException = (GrpcStatusException) cause;
            setStatus(trailers, grpcStatusException.status(), grpcStatusException.applicationStatus(), allocator);
        } else {
            setStatus(trailers, toGrpcStatus(cause), null, allocator);
        }
    }

    // This code is a copy to io.servicetalk.grpc.api.GrpcUtils.setStatus
    // but cannot be shared because we don't have an internal module for grpc code that is both using the api module
    // and is also used in the api module as that would lead to circular dependency.
    static void setStatus(final HttpHeaders trailers, final GrpcStatus status, @Nullable final Status details,
                          @Nullable final BufferAllocator allocator) {
        trailers.set(GRPC_STATUS, valueOf(status.code().value()));
        if (status.description() != null) {
            trailers.set(GRPC_STATUS_MESSAGE, status.description());
        }
        if (details != null) {
            assert allocator != null;
            trailers.set(GRPC_STATUS_DETAILS_BIN,
                    newAsciiString(allocator.wrap(Base64.getEncoder().encode(details.toByteArray()))));
        }
    }

    // This code is a copy to io.servicetalk.grpc.api.GrpcUtils.toGrpcStatus
    // but cannot be shared because we don't have an internal module for grpc code that is both using the api module
    // and is also used in the api module as that would lead to circular dependency.
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
}
