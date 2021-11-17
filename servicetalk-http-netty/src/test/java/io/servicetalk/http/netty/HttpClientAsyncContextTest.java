/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpClientAsyncContextTest {
    private static final ContextMap.Key<CharSequence> K1 = newKey("k1", CharSequence.class);
    private static final CharSequence REQUEST_ID_HEADER = newAsciiString("request-id");
    private static final CharSequence CONSUMED_REQUEST_ID_HEADER = newAsciiString("consumed-request-id");

    @Test
    void contextPreservedOverFilterBoundariesOffloaded() throws Exception {
        contextPreservedOverFilterBoundaries(false);
    }

    @Test
    void contextPreservedOverFilterBoundariesNoOffload() throws Exception {
        contextPreservedOverFilterBoundaries(true);
    }

    private static void contextPreservedOverFilterBoundaries(boolean useImmediate) throws Exception {
        Queue<Throwable> errorQueue = new ConcurrentLinkedQueue<>();

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()));
             StreamingHttpClient client = buildClient(useImmediate, errorQueue, serverContext).buildStreaming()) {
            makeClientRequestWithId(client, "1");
            assertThat("Error queue is not empty!", errorQueue, empty());
        }
    }

    @Nonnull
    private static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> buildClient(
            final boolean useImmediate, final Queue<Throwable> errorQueue, final ServerContext serverContext) {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder = HttpClients.forSingleAddress(
                serverHostAndPort(serverContext))
                .appendClientFilter(c -> new TestStreamingHttpClientFilter(c, errorQueue))
                .appendClientFilter(c -> new TestStreamingHttpClientFilter(c, errorQueue));
        if (useImmediate) {
            clientBuilder.executionStrategy(HttpExecutionStrategies.noOffloadsStrategy());
        }
        return clientBuilder;
    }

    private static void makeClientRequestWithId(StreamingHttpClient connection, String requestId)
            throws ExecutionException, InterruptedException {
        StreamingHttpRequest request = connection.get("/");
        request.headers().set(REQUEST_ID_HEADER, requestId);
        StreamingHttpResponse response = connection.request(request).toFuture().get();
        assertEquals(OK, response.status());
        response.messageBody().ignoreElements().toFuture().get();
    }

    private static void assertAsyncContext(@Nullable CharSequence requestId, Queue<Throwable> errorQueue) {
        CharSequence k1Value = AsyncContext.get(K1);
        if (requestId != null && !requestId.equals(k1Value)) {
            errorQueue.add(new AssertionError("AsyncContext[" + K1 + "]=[" + k1Value +
                    "], expected=[" + requestId + "]"));
        }
    }

    private static final class TestStreamingHttpClientFilter extends StreamingHttpClientFilter {
        private final Queue<Throwable> errorQueue;

        TestStreamingHttpClientFilter(final FilterableStreamingHttpClient delegate,
                                      Queue<Throwable> errorQueue) {
            super(delegate);
            this.errorQueue = errorQueue;
        }

        @Override
        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                        final HttpExecutionStrategy strategy,
                                                        final StreamingHttpRequest request) {
            // The first filter will remove the REQUEST_ID_HEADER and put it into AsyncContext.
            // The second filter will remove the CONSUMED_REQUEST_ID_HEADER and verify the first filter
            // put this value in AsyncContext.
            CharSequence hdrRequestId = request.headers().getAndRemove(REQUEST_ID_HEADER);
            if (hdrRequestId != null) {
                AsyncContext.put(K1, hdrRequestId);
                request.headers().add(CONSUMED_REQUEST_ID_HEADER, hdrRequestId);
            } else {
                hdrRequestId = request.headers().getAndRemove(CONSUMED_REQUEST_ID_HEADER);
                if (hdrRequestId != null) {
                    assertAsyncContext(hdrRequestId, errorQueue);
                }
            }
            final CharSequence requestId = hdrRequestId;
            final StreamingHttpRequest requestWithPayloadAssert = request.transformMessageBody(pub ->
                    pub.afterSubscriber(() -> new Subscriber<Object>() {
                        @Override
                        public void onSubscribe(final Subscription subscription) {
                            assertAsyncContext(requestId, errorQueue);
                        }

                        @Override
                        public void onNext(final Object o) {
                            assertAsyncContext(requestId, errorQueue);
                        }

                        @Override
                        public void onError(final Throwable throwable) {
                            assertAsyncContext(requestId, errorQueue);
                        }

                        @Override
                        public void onComplete() {
                            assertAsyncContext(requestId, errorQueue);
                        }
                    }));
            return delegate.request(strategy, requestWithPayloadAssert).map(resp -> {
                assertAsyncContext(requestId, errorQueue);
                return resp.transformMessageBody(pub ->
                        pub.afterSubscriber(() -> new Subscriber<Object>() {
                            @Override
                            public void onSubscribe(final Subscription subscription) {
                                assertAsyncContext(requestId, errorQueue);
                            }

                            @Override
                            public void onNext(final Object o) {
                                assertAsyncContext(requestId, errorQueue);
                            }

                            @Override
                            public void onError(final Throwable throwable) {
                                assertAsyncContext(requestId, errorQueue);
                            }

                            @Override
                            public void onComplete() {
                                assertAsyncContext(requestId, errorQueue);
                            }
                        }));
            });
        }
    }
}
