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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpRequest;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpContextKeys.STREAM_ID;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class H2StreamIdContextTest {

    private static final String X_HTTP2_STREAM_ID = "x-http2-stream-id";

    @Test
    void test() throws Exception {
        AtomicLong expectedStreamIdHolder = new AtomicLong();
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(HttpProtocol.HTTP_2.config)
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().setHeader(X_HTTP2_STREAM_ID,
                                String.valueOf(request.context().get(STREAM_ID))));
             BlockingStreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .protocols(HttpProtocol.HTTP_2.config)
                     .appendClientFilter(c -> new StreamingHttpClientFilter(c) {
                         @Override
                         protected Single<StreamingHttpResponse> request(StreamingHttpRequester delegate,
                                                                         StreamingHttpRequest request) {
                             return delegate.request(request.transformMessageBody(publisher -> publisher
                                     .beforeOnSubscribe(__ -> assertThat(
                                             "No STREAM_ID assigned before transport subscribes to payload body",
                                             request.context().get(STREAM_ID), is(expectedStreamIdHolder.get())))));
                         }
                     }).buildBlockingStreaming()) {

            for (long expectedStreamId = 3; expectedStreamId <= 7; expectedStreamId += 2) {
                expectedStreamIdHolder.set(expectedStreamId);
                // Use of the streaming API is required to delay subscribe to request payload body
                BlockingStreamingHttpRequest request = client.post("/")
                        .payloadBody(singleton(client.executionContext().bufferAllocator().fromAscii("hello")));
                assertThat("Unexpected STREAM_ID before request is sent",
                        request.context().get(STREAM_ID), is(nullValue()));
                HttpResponse response = client.request(request).toResponse().toFuture().get();
                assertThat("STREAM_ID after request is sent does not match expected value",
                        request.context().get(STREAM_ID), is(expectedStreamId));
                assertThat("Unexpected STREAM_ID in response",
                        response.context().get(STREAM_ID), is(nullValue()));
                assertThat("STREAM_ID observed by the server does not match expected value",
                        response.headers().get(X_HTTP2_STREAM_ID), contentEqualTo(Long.toString(expectedStreamId)));
            }
        }
    }

    @Test
    void testWithRetry() throws Exception {
        AtomicBoolean shouldFail = new AtomicBoolean(true);
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(HttpProtocol.HTTP_2.config)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    if (shouldFail.compareAndSet(true, false)) {
                        assertThat(request.context().get(STREAM_ID), is(3L));
                        ctx.closeAsync().toFuture().get();
                        throw DELIBERATE_EXCEPTION;
                    }
                    assertThat(request.context().get(STREAM_ID), is(5L));
                    return responseFactory.ok().setHeader(X_HTTP2_STREAM_ID,
                            String.valueOf(request.context().get(STREAM_ID)));
                });
             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .protocols(HttpProtocol.HTTP_2.config)
                     .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                             .retryOther((metaData, throwable) -> {
                                 assertThat(metaData.context().get(STREAM_ID), is(3L));
                                 return BackOffPolicy.ofImmediateBounded();
                             })
                             .build())
                     .buildBlocking()) {

            HttpRequest request = client.get("/");
            assertThat("Unexpected STREAM_ID before request is sent",
                    request.context().get(STREAM_ID), is(nullValue()));
            HttpResponse response = client.request(request);
            assertThat("STREAM_ID after request is sent does not match expected value",
                    request.context().get(STREAM_ID), is(5L));
            assertThat("Unexpected STREAM_ID in response",
                    response.context().get(STREAM_ID), is(nullValue()));
            assertThat("STREAM_ID observed by the server does not match expected value",
                    response.headers().get(X_HTTP2_STREAM_ID), contentEqualTo(Long.toString(5L)));
        }
    }
}
