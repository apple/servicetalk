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
import io.servicetalk.http.api.PayloadTooLargeException;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
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

    static Publisher<Buffer> newBufferPublisher(int sizeInBytes, BufferAllocator allocator) {
        Buffer[] buffers = new Buffer[sizeInBytes];
        for (int i = 0; i < sizeInBytes; ++i) {
            buffers[i] = allocator.fromAscii("a");
        }
        return Publisher.from(buffers);
    }

    private static FilterableStreamingHttpClient mockTransport(int responsePayloadSize) {
        return new FilterableStreamingHttpClient() {
            private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();
            @Override
            public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                    final HttpRequestMetaData metaData) {
                return failed(new UnsupportedOperationException());
            }

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return Single.succeeded(REQ_RESP_FACTORY.ok().transformPayloadBody(pub ->
                        Publisher.defer(() ->
                                pub.concat(newBufferPublisher(responsePayloadSize, DEFAULT_ALLOCATOR))
                                .shareContextOnSubscribe())));
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
