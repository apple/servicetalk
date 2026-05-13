/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.PayloadTooLargeException;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PayloadSizeLimitingHttpRequesterFilterTest {
    static final StreamingHttpRequestResponseFactory REQ_RESP_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, INSTANCE, HTTP_1_1);
    private static final HttpExecutionContext MOCK_EXECUTION_CTX = mock(HttpExecutionContext.class);

    @ParameterizedTest
    @ValueSource(ints = {99, 100})
    void lessThanEqToMaxAllowed(int payloadLen) throws ExecutionException, InterruptedException {
        new PayloadSizeLimitingHttpRequesterFilter(100).create(mockTransport(payloadLen))
                .request(REQ_RESP_FACTORY.get("/")).toFuture().get()
                .payloadBody().toFuture().get();
    }

    @Test
    void moreThanMaxRejected() {
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> new PayloadSizeLimitingHttpRequesterFilter(100).create(mockTransport(101))
                        .request(REQ_RESP_FACTORY.get("/")).toFuture().get()
                        .payloadBody().toFuture().get());
        assertThat(e.getCause(), instanceOf(PayloadTooLargeException.class));
    }

    @Test
    void contentLengthOverMaxRejectedBeforeBodyRead() {
        AtomicBoolean drained = new AtomicBoolean();
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> new PayloadSizeLimitingHttpRequesterFilter(100)
                        .create(mockTransportWithContentLength(50, "101", drained))
                        .request(REQ_RESP_FACTORY.get("/")).toFuture().get());
        assertThat(e.getCause(), instanceOf(PayloadTooLargeException.class));
        assertThat("response payload was not drained before failing", drained.get(), is(true));
    }

    @Test
    void malformedContentLengthIgnored() throws ExecutionException, InterruptedException {
        new PayloadSizeLimitingHttpRequesterFilter(100)
                .create(mockTransportWithContentLength(50, "not-a-number", null))
                .request(REQ_RESP_FACTORY.get("/")).toFuture().get()
                .payloadBody().toFuture().get();
    }

    @Test
    void contentLengthAtMaxAllowed() throws ExecutionException, InterruptedException {
        new PayloadSizeLimitingHttpRequesterFilter(100)
                .create(mockTransportWithContentLength(100, "100", null))
                .request(REQ_RESP_FACTORY.get("/")).toFuture().get()
                .payloadBody().toFuture().get();
    }

    @Test
    void lyingContentLengthStillCaughtByStreamingLimiter() {
        // A Content-Length that under-reports the body size should be caught by the HTTP message codecs
        // in practice; this test exercises the filter's defense-in-depth fallback to the streaming counter.
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> new PayloadSizeLimitingHttpRequesterFilter(100)
                        .create(mockTransportWithContentLength(101, "50", null))
                        .request(REQ_RESP_FACTORY.get("/")).toFuture().get()
                        .payloadBody().toFuture().get());
        assertThat(e.getCause(), instanceOf(PayloadTooLargeException.class));
    }

    @Test
    void headResponseWithOversizedContentLengthIsNotRejected() throws ExecutionException, InterruptedException {
        // HEAD responses advertise Content-Length of the body that *would* be returned but carry no payload
        // (RFC 9110 §9.3.2). The early check must not fail these.
        new PayloadSizeLimitingHttpRequesterFilter(100)
                .create(mockTransportWithResponse(0, "1000000", OK))
                .request(REQ_RESP_FACTORY.newRequest(HEAD, "/")).toFuture().get()
                .payloadBody().toFuture().get();
    }

    @Test
    void noContentResponseWithOversizedContentLengthIsNotRejected()
            throws ExecutionException, InterruptedException {
        new PayloadSizeLimitingHttpRequesterFilter(100)
                .create(mockTransportWithResponse(0, "1000000", NO_CONTENT))
                .request(REQ_RESP_FACTORY.get("/")).toFuture().get()
                .payloadBody().toFuture().get();
    }

    @Test
    void notModifiedResponseWithOversizedContentLengthIsNotRejected()
            throws ExecutionException, InterruptedException {
        new PayloadSizeLimitingHttpRequesterFilter(100)
                .create(mockTransportWithResponse(0, "1000000", NOT_MODIFIED))
                .request(REQ_RESP_FACTORY.get("/")).toFuture().get()
                .payloadBody().toFuture().get();
    }

    static Publisher<Buffer> newBufferPublisher(int sizeInBytes, BufferAllocator allocator) {
        Buffer[] buffers = new Buffer[sizeInBytes];
        for (int i = 0; i < sizeInBytes; ++i) {
            buffers[i] = allocator.fromAscii("a");
        }
        return Publisher.from(buffers);
    }

    private static FilterableStreamingHttpClient mockTransport(int responsePayloadSize) {
        return mockTransportWithContentLength(responsePayloadSize, null, null);
    }

    private static FilterableStreamingHttpClient mockTransportWithContentLength(
            int responsePayloadSize, @Nullable final String contentLength, @Nullable final AtomicBoolean drained) {
        return mockTransportInternal(responsePayloadSize, contentLength, drained, OK);
    }

    private static FilterableStreamingHttpClient mockTransportWithResponse(
            int responsePayloadSize, @Nullable final String contentLength, final HttpResponseStatus status) {
        return mockTransportInternal(responsePayloadSize, contentLength, null, status);
    }

    private static FilterableStreamingHttpClient mockTransportInternal(
            int responsePayloadSize, @Nullable final String contentLength, @Nullable final AtomicBoolean drained,
            final HttpResponseStatus status) {
        return new FilterableStreamingHttpClient() {
            private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();
            @Override
            public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                    final HttpRequestMetaData metaData) {
                return failed(new UnsupportedOperationException());
            }

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                StreamingHttpResponse response = REQ_RESP_FACTORY.newResponse(status).transformPayloadBody(pub ->
                        Publisher.defer(() -> {
                            Publisher<Buffer> body = pub.concat(newBufferPublisher(responsePayloadSize,
                                    DEFAULT_ALLOCATOR));
                            if (drained != null) {
                                body = body.afterFinally(() -> drained.set(true));
                            }
                            return body.shareContextOnSubscribe();
                        }));
                if (contentLength != null) {
                    response.headers().set(CONTENT_LENGTH, contentLength);
                }
                return Single.succeeded(response);
            }

            @Override
            public HttpExecutionContext executionContext() {
                return MOCK_EXECUTION_CTX;
            }

            @Override
            public StreamingHttpResponseFactory httpResponseFactory() {
                return REQ_RESP_FACTORY;
            }

            @Override
            public Completable onClose() {
                return closeable.onClose();
            }

            @Override
            public Completable onClosing() {
                return closeable.onClosing();
            }

            @Override
            public Completable closeAsync() {
                return closeable.closeAsync();
            }

            @Override
            public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
                return REQ_RESP_FACTORY.newRequest(method, requestTarget);
            }
        };
    }
}
