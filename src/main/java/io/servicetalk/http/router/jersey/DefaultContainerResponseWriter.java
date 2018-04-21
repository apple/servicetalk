/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpResponseStatuses;

import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;

import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;

import static io.servicetalk.http.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.router.jersey.CharSequenceUtil.asCharSequence;
import static java.util.Arrays.stream;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

final class DefaultContainerResponseWriter implements ContainerResponseWriter {
    private static final Map<Status, HttpResponseStatus> RESPONSE_STATUSES =
            unmodifiableMap(stream(Status.values())
                    .collect(toMap(identity(),
                            s -> HttpResponseStatuses.getResponseStatus(s.getStatusCode(), s.getReasonPhrase()))));

    private static final int UNKNOWN_RESPONSE_LENGTH = -1;
    private static final int EMPTY_RESPONSE = 0;

    private final HttpRequest<HttpPayloadChunk> request;
    private final BufferAllocator allocator;
    private final Ref<Publisher<HttpPayloadChunk>> chunkPublisherRef;

    private HttpResponse<HttpPayloadChunk> response;

    DefaultContainerResponseWriter(final HttpRequest<HttpPayloadChunk> request,
                                   final BufferAllocator allocator,
                                   final Ref<Publisher<HttpPayloadChunk>> chunkPublisherRef) {
        this.request = request;
        this.allocator = requireNonNull(allocator);
        this.chunkPublisherRef = requireNonNull(chunkPublisherRef);

        // Set a default response used in case of failure
        response = newResponse(request.getVersion(), INTERNAL_SERVER_ERROR);
    }

    HttpResponse<HttpPayloadChunk> getResponse() {
        return response;
    }

    @Nullable
    @Override
    public OutputStream writeResponseStatusAndHeaders(final long contentLength, final ContainerResponse responseContext)
            throws ContainerException {

        @Nullable
        final Publisher<HttpPayloadChunk> chunkPublisher = chunkPublisherRef.get();
        // contentLength is >= 0 if the entity content length in bytes is known to Jersey, otherwise -1
        if (chunkPublisher != null) {
            response = createResponse(request, UNKNOWN_RESPONSE_LENGTH, chunkPublisher, responseContext);
            return null;
        } else if (contentLength == 0) {
            response = createResponse(request, EMPTY_RESPONSE, null, responseContext);
            return null;
        } else {
            final DummyChunkPublisherOutputStream bpos = new DummyChunkPublisherOutputStream(allocator);
            response = createResponse(request, contentLength, bpos.getChunkPublisher(), responseContext);
            return bpos;
        }

        // TODO send flush signal when supported
    }

    @Override
    public boolean suspend(final long timeOut, final TimeUnit timeUnit, final TimeoutHandler timeoutHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSuspendTimeout(final long timeOut, final TimeUnit timeUnit) throws IllegalStateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void commit() {
        // TODO send flush signal when supported
    }

    @Override
    public void failure(final Throwable error) {
        // Rethrow the original exception as required by JAX-RS, 3.3.4.
        if (error instanceof RuntimeException) {
            throw (RuntimeException) error;
        } else {
            throw new ContainerException(error);
        }
    }

    @Override
    public boolean enableResponseBuffering() {
        // Allow the Jersey infrastructure (including media serializers) to buffer small (configurable) responses
        // in order to compute responses' content length. Setting false here would make all responses chunked,
        // which can be suboptimal for some REST clients that have optimized code paths when the response size is known.
        // Note that this buffering can be bypassed by setting the following configuration property to 0:
        //
        //     org.glassfish.jersey.CommonProperties#OUTBOUND_CONTENT_LENGTH_BUFFER}
        return true;
    }

    private HttpResponse<HttpPayloadChunk> createResponse(final HttpRequest<HttpPayloadChunk> request,
                                                          final long contentLength,
                                                          @Nullable final Publisher<HttpPayloadChunk> content,
                                                          final ContainerResponse containerResponse) {
        final HttpResponse<HttpPayloadChunk> response;
        final HttpResponseStatus status = getStatus(containerResponse);
        if (content != null) {
            response = newResponse(request.getVersion(), status, content);
        } else {
            response = newResponse(request.getVersion(), status);
        }

        final HttpHeaders headers = response.getHeaders();
        containerResponse.getHeaders().forEach((k, vs) -> vs.forEach(v -> {
            if (v != null) {
                headers.add(k, asCharSequence(v));
            }
        }));

        if (contentLength == UNKNOWN_RESPONSE_LENGTH) {
            if (!headers.contains(CONTENT_LENGTH)) {
                headers.set(TRANSFER_ENCODING, CHUNKED);
            }
        } else {
            headers.set(CONTENT_LENGTH, Long.toString(contentLength));
            removeChunkedEncoding(headers);
        }

        return response;
    }

    private static HttpResponseStatus getStatus(final ContainerResponse containerResponse) {
        final StatusType statusInfo = containerResponse.getStatusInfo();
        return statusInfo instanceof Status ? RESPONSE_STATUSES.get(statusInfo) :
                HttpResponseStatuses.getResponseStatus(statusInfo.getStatusCode(), statusInfo.getReasonPhrase());
    }

    private static void removeChunkedEncoding(final HttpHeaders headers) {
        for (final Iterator<? extends CharSequence> i = headers.getAll(TRANSFER_ENCODING); i.hasNext();) {
            if (contentEqualsIgnoreCase(CHUNKED, i.next())) {
                i.remove();
            }
        }
    }
}
