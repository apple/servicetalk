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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.Single.Subscriber;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.ConnectableOutputStream;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

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

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_DIRECT_RO_ALLOCATOR;
import static io.servicetalk.http.api.CharSequences.emptyAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatuses.getResponseStatus;
import static io.servicetalk.http.router.jersey.CharSequenceUtils.asCharSequence;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.getResponseBufferPublisher;
import static io.servicetalk.http.router.jersey.internal.RequestProperties.getResponseExecutorOffloader;
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
                                    PREFER_DIRECT_RO_ALLOCATOR.fromAscii(s.getReasonPhrase())))));

    private static final int UNKNOWN_RESPONSE_LENGTH = -1;

    private final ContainerRequest request;
    private final HttpProtocolVersion protocolVersion;
    private final HttpServiceContext serviceCtx;
    private final StreamingHttpResponseFactory responseFactory;
    private final Subscriber<? super StreamingHttpResponse> responseSubscriber;

    @Nullable
    private volatile Cancellable suspendedTimerCancellable;

    @Nullable
    private volatile Runnable suspendedTimeoutRunnable;

    DefaultContainerResponseWriter(final ContainerRequest request,
                                   final HttpProtocolVersion protocolVersion,
                                   final HttpServiceContext serviceCtx,
                                   final StreamingHttpResponseFactory responseFactory,
                                   final Subscriber<? super StreamingHttpResponse> responseSubscriber) {
        this.request = requireNonNull(request);
        this.protocolVersion = requireNonNull(protocolVersion);
        this.serviceCtx = requireNonNull(serviceCtx);
        this.responseFactory = requireNonNull(responseFactory);
        this.responseSubscriber = requireNonNull(responseSubscriber);
    }

    @Nullable
    @Override
    public OutputStream writeResponseStatusAndHeaders(final long contentLength, final ContainerResponse responseContext)
            throws ContainerException {

        final Publisher<Buffer> content = getResponseBufferPublisher(request);
        // contentLength is >= 0 if the entity content length in bytes is known to Jersey, otherwise -1
        if (content != null) {
            sendResponse(UNKNOWN_RESPONSE_LENGTH, content, responseContext);
            return null;
        } else if (contentLength == 0 || isHeadRequest()) {
            sendResponse(contentLength, null, responseContext);
            return null;
        } else {
            final ConnectableOutputStream os = new ConnectableOutputStream();
            sendResponse(contentLength, os.connect().map(bytes ->
                            serviceCtx.getExecutionContext().getBufferAllocator().wrap(bytes)),
                    responseContext);
            return os;
        }
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
        if (timeOut > 0) {
            suspendedTimerCancellable = serviceCtx.getExecutionContext().getExecutor()
                    .schedule(r, timeOut, timeUnit);
        }
    }

    @Override
    public void commit() {
        suspendedTimeoutRunnable = null;
        cancelSuspendedTimer();
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
                              @Nullable final Publisher<Buffer> content,
                              final ContainerResponse containerResponse) {

        final HttpResponseStatus status = getStatus(containerResponse);
        final StreamingHttpResponse response;
        if (content != null && !isHeadRequest()) {
            final Executor executor = getResponseExecutorOffloader(request);
            // TODO(scott): use request factory methods that accept a payload body to avoid overhead of payloadBody.
            response = responseFactory.newResponse(status)
                    .version(protocolVersion)
                    .payloadBody(executor != null ? content.subscribeOn(executor) : content);
        } else {
            response = responseFactory.newResponse(status).version(protocolVersion);
        }

        final HttpHeaders headers = response.headers();
        containerResponse.getHeaders().forEach((k, vs) -> vs.forEach(v -> {
            headers.add(k, v == null ? emptyAsciiString() : asCharSequence(v));
        }));

        if (!headers.contains(CONTENT_LENGTH)) {
            if (contentLength == UNKNOWN_RESPONSE_LENGTH) {
                // We can omit Transfer-Encoding for HEAD per https://tools.ietf.org/html/rfc7231#section-4.3.2
                if (!isHeadRequest()) {
                    headers.set(TRANSFER_ENCODING, CHUNKED);
                }
            } else {
                headers.set(CONTENT_LENGTH, contentLength == 0 ? ZERO : Long.toString(contentLength));
                headers.remove(TRANSFER_ENCODING, CHUNKED, true);
            }
        }

        responseSubscriber.onSuccess(response);
    }

    private HttpResponseStatus getStatus(final ContainerResponse containerResponse) {
        final StatusType statusInfo = containerResponse.getStatusInfo();
        return statusInfo instanceof Status ? RESPONSE_STATUSES.get(statusInfo) :
                getResponseStatus(statusInfo.getStatusCode(), serviceCtx.getExecutionContext()
                        .getBufferAllocator().fromAscii(statusInfo.getReasonPhrase()));
    }

    private boolean isHeadRequest() {
        return HEAD.equals(request.getMethod());
    }
}
