/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HttpMessageDiscardWatchdogClientFilterTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    /**
     * Asserts that the response message payload is cleaned up properly if discarded in a filter and not
     * properly cleaned up by the filter body.
     */
    @ParameterizedTest(name = "{displayName} [{index}] transformer={0}")
    @MethodSource("responseTransformers")
    void cleansClientResponseMessageBodyIfDiscarded(ResponseTransformer transformer,
                                                    @Nullable Class<?> expectedException)
            throws Exception {
        final AtomicLong numConnectionsOpened = new AtomicLong(0);

        try (HttpServerContext serverContext = newServerBuilder(SERVER_CTX)
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        Single.fromSupplier(() -> responseFactory.ok().payloadBody(Publisher.from(ctx.executionContext()
                                .bufferAllocator().fromUtf8("Hello, World!")))))) {
            try (StreamingHttpClient client = newClientBuilder(serverContext, CLIENT_CTX)
                    .appendConnectionFactoryFilter(original ->
                            new DelegatingConnectionFactory<InetSocketAddress,
                                    FilterableStreamingHttpConnection>(original) {
                        @Override
                        public Single<FilterableStreamingHttpConnection> newConnection(
                                final InetSocketAddress inetSocketAddress,
                                @Nullable final ContextMap context,
                                @Nullable final TransportObserver observer) {
                            numConnectionsOpened.incrementAndGet();
                            return delegate().newConnection(inetSocketAddress, context, observer);
                        }
                    })
                    .appendClientFilter(c -> new StreamingHttpClientFilter(c) {
                        @Override
                        protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                        final StreamingHttpRequest request) {
                            return transformer.apply(delegate(), request);
                        }
                    })
                    .buildStreaming()) {

                int numRequests = 5;
                for (int i = 0; i < numRequests; i++) {
                    if (expectedException == null) {
                        StreamingHttpResponse response = client.request(client.get("/")).toFuture().get();
                        assertEquals(HttpResponseStatus.OK, response.status());
                        // Consume the body to release the connection back to the pool
                        response.messageBody().ignoreElements().toFuture().get();
                    } else {
                        ExecutionException ex = assertThrows(ExecutionException.class,
                                () -> client.request(client.get("/")).toFuture().get());
                        assertTrue(ex.getCause().getClass().isAssignableFrom(expectedException));
                    }
                }
                assertEquals(1, numConnectionsOpened.get());
            }
        }
    }

    private static Stream<Arguments> responseTransformers() {
        return Stream.of(
                Arguments.of(new ResponseTransformer() {
                    @Override
                    public Single<StreamingHttpResponse> apply(final StreamingHttpRequester requester,
                                                               final StreamingHttpRequest request) {
                        return requester.request(request);
                    }

                    @Override
                    public String toString() {
                        return "Just delegation, no failure";
                    }
                }, null),
                Arguments.of(new ResponseTransformer() {
                    @Override
                    public Single<StreamingHttpResponse> apply(final StreamingHttpRequester requester,
                                                               final StreamingHttpRequest request) {
                        return requester
                                .request(request)
                                .map(dropped -> {
                                    throw new DeliberateException();
                                });
                    }

                    @Override
                    public String toString() {
                        return "Throws exception in filter which drops message";
                    }
                }, DeliberateException.class)
        );
    }

    interface ResponseTransformer
            extends BiFunction<StreamingHttpRequester, StreamingHttpRequest, Single<StreamingHttpResponse>> { }
}
