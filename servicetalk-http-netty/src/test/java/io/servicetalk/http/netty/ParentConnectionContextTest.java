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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpConnection;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpConnection;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.AddressUtils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class ParentConnectionContextTest {

    private final BlockingQueue<Throwable> errors = new LinkedBlockingQueue<>();

    @ParameterizedTest(name = "{index}: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testAsyncAggregated(HttpProtocol protocol) throws Exception {
        try (ServerContext serverContext = newServerBuilder(protocol)
                .listenAndAwait((ctx, request, responseFactory) -> {
                    assertServiceContext(ctx, protocol, errors);
                    return succeeded(responseFactory.ok());
                });
             HttpClient client = newClientBuilder(serverContext, protocol).build();
             HttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {

            assertClientConnectionContext(connection.connectionContext());
            HttpResponse response = connection.request(connection.get("/")).toFuture().get();
            assertResponse(response);
        }
        assertNoAsyncErrors(errors);
    }

    @ParameterizedTest(name = "{index}: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testAsyncStreaming(HttpProtocol protocol) throws Exception {
        try (ServerContext serverContext = newServerBuilder(protocol)
                .listenStreamingAndAwait((ctx, request, responseFactory) -> {
                    assertServiceContext(ctx, protocol, errors);
                    return succeeded(responseFactory.ok());
                });
             StreamingHttpClient client = newClientBuilder(serverContext, protocol).buildStreaming();
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {

            assertClientConnectionContext(connection.connectionContext());
            StreamingHttpResponse response = connection.request(connection.get("/")).toFuture().get();
            assertResponse(response);
            response.payloadBody().toFuture().get();
        }
        assertNoAsyncErrors(errors);
    }

    @ParameterizedTest(name = "{index}: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testBlockingAggregated(HttpProtocol protocol) throws Exception {
        try (ServerContext serverContext = newServerBuilder(protocol)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    assertServiceContext(ctx, protocol, errors);
                    return responseFactory.ok();
                });
             BlockingHttpClient client = newClientBuilder(serverContext, protocol).buildBlocking();
             BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            assertClientConnectionContext(connection.connectionContext());
            HttpResponse response = connection.request(connection.get("/"));
            assertResponse(response);
        }
        assertNoAsyncErrors(errors);
    }

    @ParameterizedTest(name = "{index}: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testBlockingStreaming(HttpProtocol protocol) throws Exception {
        try (ServerContext serverContext = newServerBuilder(protocol)
                .listenBlockingStreamingAndAwait((ctx, request, response) -> {
                    assertServiceContext(ctx, protocol, errors);
                    response.sendMetaData().close();
                });
             BlockingStreamingHttpClient client = newClientBuilder(serverContext, protocol).buildBlockingStreaming();
             BlockingStreamingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            assertClientConnectionContext(connection.connectionContext());
            BlockingStreamingHttpResponse response = connection.request(connection.get("/"));
            assertResponse(response);
            response.payloadBody().forEach(__ -> { /* noop */ });
        }
        assertNoAsyncErrors(errors);
    }

    private static void assertResponse(HttpResponseMetaData response) {
        assertThat(response.status(), is(OK));
    }

    private static void assertClientConnectionContext(HttpConnectionContext ctx) {
        assertThat(ctx, is(notNullValue()));
        assertThat("Unexpected ConnectionContext#parent() for the client connection",
                ctx.parent(), is(nullValue()));
    }

    private static void assertServiceContext(HttpServiceContext ctx, HttpProtocol protocol,
                                             BlockingQueue<Throwable> errors) {
        try {
            assertThat(ctx, is(notNullValue()));
            ConnectionContext parent = ctx.parent();
            switch (protocol) {
                case HTTP_1:
                    assertThat("Unexpected HttpServiceContext#parent() for HTTP/1.x connection",
                            parent, is(nullValue()));
                    break;
                case HTTP_2:
                    assertThat("HTTP/2 stream must have reference to its parent ConnectionContext",
                            parent, is(notNullValue()));
                    assertThat("Unexpected localAddress",
                            parent.localAddress(), is(sameInstance(ctx.localAddress())));
                    assertThat("Unexpected remoteAddress",
                            parent.remoteAddress(), is(sameInstance(ctx.remoteAddress())));
                    assertThat("Unexpected parent.parent()", parent.parent(), is(nullValue()));
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unexpected " + HttpProtocol.class.getSimpleName() + ": " + protocol);
            }
        } catch (Throwable t) {
            errors.add(t);
        }
    }

    private static HttpServerBuilder newServerBuilder(HttpProtocol protocol) {
        return HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config);
    }

    private static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilder(
            ServerContext serverContext, HttpProtocol protocol) {
        return HttpClients.forSingleAddress(AddressUtils.serverHostAndPort(serverContext))
                .protocols(protocol.config);
    }
}
