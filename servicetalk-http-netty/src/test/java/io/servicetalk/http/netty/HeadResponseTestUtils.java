/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.transport.api.ServerContext;

import java.util.function.Function;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Server setup and the reserved-connection check shared by {@link Http1HeadResponseTest} and
 * {@link Http2HeadResponseTest}.
 */
final class HeadResponseTestUtils {

    static final String BODY = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
    static final String TRAILER_NAME = "x-trailer";
    static final String TRAILER_VALUE = "tv";

    /** Starts a server whose handler produces the response under test. */
    @FunctionalInterface
    interface ServerStarter {
        ServerContext start() throws Exception;
    }

    private HeadResponseTestUtils() {
        // No instances
    }

    static Buffer body() {
        return DEFAULT_ALLOCATOR.fromAscii(BODY);
    }

    /** Adds the {@link #TRAILER_NAME} trailer to the response. */
    static StreamingHttpResponse withTrailer(StreamingHttpResponse response) {
        return response.transform(new StatelessTrailersTransformer<Buffer>() {
            @Override
            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                return trailers.add(TRAILER_NAME, TRAILER_VALUE);
            }
        });
    }

    static ServerStarter streaming(HttpProtocolConfig protocol,
                                   Function<StreamingHttpResponseFactory, StreamingHttpResponse> handler) {
        return () -> HttpServers.forAddress(localAddress(0)).protocols(protocol)
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        Single.succeeded(handler.apply(responseFactory)));
    }

    static ServerStarter aggregated(HttpProtocolConfig protocol,
                                    Function<HttpResponseFactory, HttpResponse> handler) {
        return () -> HttpServers.forAddress(localAddress(0)).protocols(protocol)
                .listenAndAwait((ctx, request, responseFactory) -> Single.succeeded(handler.apply(responseFactory)));
    }

    static ServerStarter blocking(HttpProtocolConfig protocol,
                                  Function<HttpResponseFactory, HttpResponse> handler) {
        return () -> HttpServers.forAddress(localAddress(0)).protocols(protocol)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> handler.apply(responseFactory));
    }

    /**
     * Sends a {@code HEAD} and then a {@code GET} on one reserved connection to a server that answers with a body and
     * a trailer, and checks that the {@code HEAD} response has neither and the {@code GET} response has both.
     */
    static void assertHeadThenGetOnReservedConnection(HttpProtocolConfig protocol,
                                                      HttpTestExecutionStrategy strategy) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0)).protocols(protocol)
                .listenStreamingAndAwait((ctx, request, responseFactory) -> Single.succeeded(
                        withTrailer(responseFactory.ok().payloadBody(Publisher.from(body())))));
             StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .protocols(protocol).executionStrategy(strategy.executorSupplier.get()).buildStreaming();
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            HttpResponse head = connection.request(connection.head("/")).toFuture().get().toResponse()
                    .toFuture().get();
            assertThat(head.payloadBody().readableBytes(), is(0));
            assertThat(head.trailers().isEmpty(), is(true));

            HttpResponse get = connection.request(connection.get("/")).toFuture().get().toResponse()
                    .toFuture().get();
            assertThat(get.payloadBody().toString(ISO_8859_1), is(BODY));
            assertThat(get.trailers().get(TRAILER_NAME).toString(), is(TRAILER_VALUE));
        }
    }
}
