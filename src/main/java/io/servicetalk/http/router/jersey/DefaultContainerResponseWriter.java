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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;

import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;

import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;

import static io.servicetalk.buffer.ReadOnlyBufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.http.api.CharSequences.emptyAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpResponseStatuses.getResponseStatus;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.router.jersey.CharSequenceUtils.asCharSequence;
import static java.util.Arrays.stream;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static javax.ws.rs.HttpMethod.HEAD;

final class DefaultContainerResponseWriter implements ContainerResponseWriter {
    private static final Map<Status, HttpResponseStatus> RESPONSE_STATUSES =
            unmodifiableMap(stream(Status.values())
                    .collect(toMap(identity(),
                            s -> getResponseStatus(s.getStatusCode(),
                                    PREFER_DIRECT_ALLOCATOR.fromAscii(s.getReasonPhrase())))));

    private static final int UNKNOWN_RESPONSE_LENGTH = -1;
    private static final int EMPTY_RESPONSE = 0;

    private final ContainerRequest request;
    private final HttpProtocolVersion protocolVersion;
    private final BufferAllocator allocator;
    private final Executor executor;
    private final Ref<Publisher<HttpPayloadChunk>> chunkPublisherRef;
    private final Subscriber<? super HttpResponse<HttpPayloadChunk>> responseSubscriber;

    @Nullable
    private volatile Cancellable suspendedTimerCancellable;

    @Nullable
    private volatile Runnable suspendedTimeoutRunnable;

    DefaultContainerResponseWriter(final ContainerRequest request,
                                   final HttpProtocolVersion protocolVersion,
                                   final BufferAllocator allocator,
                                   final Executor executor,
                                   final Ref<Publisher<HttpPayloadChunk>> chunkPublisherRef,
                                   final Subscriber<? super HttpResponse<HttpPayloadChunk>> responseSubscriber) {
        this.request = requireNonNull(request);
        this.protocolVersion = requireNonNull(protocolVersion);
        this.allocator = requireNonNull(allocator);
        this.executor = requireNonNull(executor);
        this.chunkPublisherRef = requireNonNull(chunkPublisherRef);
        this.responseSubscriber = requireNonNull(responseSubscriber);
    }

    @Nullable
    @Override
    public OutputStream writeResponseStatusAndHeaders(final long contentLength, final ContainerResponse responseContext)
            throws ContainerException {

        @Nullable
        final Publisher<HttpPayloadChunk> chunkPublisher = chunkPublisherRef.get();
        // contentLength is >= 0 if the entity content length in bytes is known to Jersey, otherwise -1
        if (chunkPublisher != null) {
            sendResponse(UNKNOWN_RESPONSE_LENGTH, chunkPublisher, responseContext);
            return null;
        } else if (contentLength == 0) {
            sendResponse(EMPTY_RESPONSE, null, responseContext);
            return null;
        } else if (HEAD.equals(request.getMethod())) {
            sendResponse(contentLength, null, responseContext);
            return null;
        } else {
            final DummyChunkPublisherOutputStream bpos = new DummyChunkPublisherOutputStream(allocator);
            sendResponse(contentLength, bpos.getChunkPublisher(), responseContext);
            return bpos;
        }

        // TODO send flush signal when supported
    }

    @Override
    public boolean suspend(final long timeOut, final TimeUnit timeUnit, final TimeoutHandler timeoutHandler) {
        // This timeoutHandler is not the one provided by users but a Jersey wrapper
        // that catches any throwable and converts them into error responses,
        // thus it is safe to use this runnable in doAfterComplete (below).
        // Also note that Jersey maintains a suspended/resumed state so if this fires after
        // the response has resumed, it will actually be a NOOP.
        // So there's no strong requirement for cancelling timer on commit/failure (below)
        // besides cleaning up our resources.
        final Runnable r = () -> timeoutHandler.onTimeout(this);

        suspendedTimeoutRunnable = r;
        scheduleSuspendedTimer(timeOut, timeUnit, r);
        return true;
    }

    @Override
    public void setSuspendTimeout(final long timeOut, final TimeUnit timeUnit) throws IllegalStateException {
        final Runnable r = suspendedTimeoutRunnable;
        if (r == null) {
            throw new IllegalStateException("Request is not suspended");
        }

        cancelSuspendedTimer();
        scheduleSuspendedTimer(timeOut, timeUnit, r);
    }

    private void scheduleSuspendedTimer(final long timeOut, final TimeUnit timeUnit, final Runnable r) {
        // timeOut<=0 means processing is suspended indefinitely: no need to actually schedule a task
        if (timeOut <= 0) {
            return;
        }

        suspendedTimerCancellable =
                executor.schedule(timeOut, timeUnit)
                        .doAfterComplete(r)
                        .subscribe();
    }

    @Override
    public void commit() {
        suspendedTimeoutRunnable = null;
        cancelSuspendedTimer();

        // TODO send flush signal when supported
    }

    @Override
    public void failure(final Throwable error) {
        suspendedTimeoutRunnable = null;
        cancelSuspendedTimer();
        responseSubscriber.onError(error);
    }

    void cancelSuspendedTimer() {
        final Cancellable c = suspendedTimerCancellable;
        if (c != null) {
            c.cancel();
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

    private void sendResponse(final long contentLength,
                              @Nullable final Publisher<HttpPayloadChunk> content,
                              final ContainerResponse containerResponse) {

        final HttpResponse<HttpPayloadChunk> response;
        final HttpResponseStatus status = getStatus(containerResponse);
        if (content != null) {
            response = newResponse(protocolVersion, status, content);
        } else {
            response = newResponse(protocolVersion, status);
        }

        final HttpHeaders headers = response.getHeaders();
        containerResponse.getHeaders().forEach((k, vs) -> vs.forEach(v -> {
            headers.add(k, v == null ? emptyAsciiString() : asCharSequence(v));
        }));

        if (contentLength == UNKNOWN_RESPONSE_LENGTH) {
            if (!headers.contains(CONTENT_LENGTH)) {
                headers.set(TRANSFER_ENCODING, CHUNKED);
            }
        } else {
            headers.set(CONTENT_LENGTH, Long.toString(contentLength));
            headers.remove(TRANSFER_ENCODING, CHUNKED, true);
        }

        responseSubscriber.onSuccess(response);
    }

    private HttpResponseStatus getStatus(final ContainerResponse containerResponse) {
        final StatusType statusInfo = containerResponse.getStatusInfo();
        return statusInfo instanceof Status ? RESPONSE_STATUSES.get(statusInfo) :
                getResponseStatus(statusInfo.getStatusCode(),
                        allocator.fromAscii(statusInfo.getReasonPhrase()));
    }
}
