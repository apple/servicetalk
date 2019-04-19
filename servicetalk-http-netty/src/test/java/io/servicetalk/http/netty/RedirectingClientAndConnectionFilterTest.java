/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.FilterableReservedStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ReservedStreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.utils.RedirectingHttpRequesterFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.net.InetSocketAddress;

import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PERMANENT_REDIRECT;
import static io.servicetalk.http.netty.RedirectingClientAndConnectionFilterTest.Type.Client;
import static io.servicetalk.http.netty.RedirectingClientAndConnectionFilterTest.Type.Connection;
import static io.servicetalk.http.netty.RedirectingClientAndConnectionFilterTest.Type.Reserved;
import static io.servicetalk.transport.netty.internal.AddressUtils.hostHeader;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static java.lang.String.format;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * This test-case is for integration testing the {@link RedirectingHttpRequesterFilter} with the various types
 * of {@link HttpClient} and {@link HttpConnection} builders.
 */
@RunWith(Parameterized.class)
public final class RedirectingClientAndConnectionFilterTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final Type type;

    protected enum Type { Client, Connection, Reserved }

    public RedirectingClientAndConnectionFilterTest(Type type) {
        this.type = type;
    }

    @Parameters(name = "{0}")
    public static Object[] getParameters() {
        return new Object[]{Client, Connection, Reserved};
    }

    private BlockingHttpRequester newRequester(ServerContext serverContext) throws Exception {
        final InetSocketAddress serverSocketAddress = (InetSocketAddress) serverContext.listenAddress();
        final HostAndPort serverHostAndPort = HostAndPort.of(serverSocketAddress);
        switch (type) {
            case Client:
                return HttpClients.forSingleAddress(serverHostAndPort)
                        .appendClientFilter(r -> r.headers().contains("X-REDIRECT"),
                                new RedirectingHttpRequesterFilter())
                        .buildBlocking();
            case Reserved:
                CompositeCloseable closeables = AsyncCloseables.newCompositeCloseable();
                try {
                    StreamingHttpClient client = closeables.prepend(HttpClients.forSingleAddress(serverHostAndPort)
                            .appendClientFilter((clientFilter, __) -> new StreamingHttpClientFilter(clientFilter) {
                                @Override
                                public Single<? extends FilterableReservedStreamingHttpConnection> reserveConnection(
                                        final HttpExecutionStrategy strategy, final HttpRequestMetaData metaData) {
                                    return delegate().reserveConnection(strategy, metaData).map(r ->
                                            new ReservedStreamingHttpConnectionFilter(closeables.prepend(r)) {
                                                @Override
                                                public Completable closeAsync() {
                                                    return closeables.closeAsync();
                                                }

                                                @Override
                                                public Completable closeAsyncGracefully() {
                                                    return closeables.closeAsyncGracefully();
                                                }
                                            }
                                    );
                                }
                            })
                            .appendClientFilter(r -> r.headers().contains("X-REDIRECT"),
                                    new RedirectingHttpRequesterFilter())
                            .buildStreaming());
                    return client.asBlockingClient().reserveConnection(client.get(""));
                } catch (Throwable t) {
                    closeables.close();
                    throw t;
                }
            case Connection:
                return new DefaultHttpConnectionBuilder<>()
                        .appendConnectionFilter(r -> r.headers().contains("X-REDIRECT"),
                                new RedirectingHttpRequesterFilter())
                        .buildBlocking(serverSocketAddress);

            default:
                throw new IllegalArgumentException(type.name());
        }
    }

    @Test
    public void redirectFilterNoHostHeaderRelativeLocation() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    if (request.requestTarget().equals("/")) {
                        return responseFactory.permanentRedirect()
                                .addHeader(LOCATION, "/next");
                    }
                    return responseFactory.ok();
                }); BlockingHttpRequester client = newRequester(serverContext)) {

            HttpRequest request = client.get("/");
            HttpResponse response = client.request(noOffloadsStrategy(), request);
            assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

            response = client.request(noOffloadsStrategy(), request.addHeader("X-REDIRECT", "TRUE"));
            assertThat(response.status(), equalTo(OK));

            // HTTP/1.0 doesn't support HOST, ensure that we don't get any errors and fallback to redirect
            response = client.request(noOffloadsStrategy(),
                    client.get("/")
                            .version(HTTP_1_0)
                            .addHeader("X-REDIRECT", "TRUE"));
            assertThat(response.status(), equalTo(PERMANENT_REDIRECT));
        }
    }

    @Test
    public void redirectFilterNoHostHeaderAbsoluteLocation() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    if (request.requestTarget().equals("/")) {
                        InetSocketAddress socketAddress = (InetSocketAddress) ctx.localAddress();
                        return responseFactory.permanentRedirect().addHeader(LOCATION,
                                format("http://%s/next", hostHeader(HostAndPort.of(socketAddress))));
                    }
                    return responseFactory.ok();
                }); BlockingHttpRequester client = newRequester(serverContext)) {

            HttpRequest request = client.get("/");
            HttpResponse response = client.request(noOffloadsStrategy(), request);
            assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

            response = client.request(noOffloadsStrategy(), request.addHeader("X-REDIRECT", "TRUE"));
            assertThat(response.status(), equalTo(OK));

            // HTTP/1.0 doesn't support HOST, ensure that we don't get any errors and fallback to redirect
            response = client.request(noOffloadsStrategy(),
                    client.get("/")
                            .version(HTTP_1_0)
                            .addHeader("X-REDIRECT", "TRUE"));
            assertThat(response.status(), equalTo(PERMANENT_REDIRECT));
        }
    }

    @Test
    public void redirectFilterWithHostHeaderRelativeLocation() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    if (request.requestTarget().equals("/")) {
                        return responseFactory.permanentRedirect()
                                .addHeader(LOCATION, "/next");
                    }
                    return responseFactory.ok();
                }); BlockingHttpRequester client = newRequester(serverContext)) {

            HttpRequest request = client.get("/").addHeader(HOST, "servicetalk.io");
            HttpResponse response = client.request(noOffloadsStrategy(), request);
            assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

            response = client.request(noOffloadsStrategy(), request.addHeader("X-REDIRECT", "TRUE"));
            assertThat(response.status(), equalTo(OK));
        }
    }

    @Test
    public void redirectFilterWithHostHeaderAbsoluteLocation() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    if (request.requestTarget().equals("/")) {
                        return responseFactory.permanentRedirect()
                                .addHeader(LOCATION, "http://servicetalk.io/next");
                    }
                    return responseFactory.ok();
                }); BlockingHttpRequester client = newRequester(serverContext)) {

            HttpRequest request = client.get("/").addHeader(HOST, "servicetalk.io");
            HttpResponse response = client.request(noOffloadsStrategy(), request);
            assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

            response = client.request(noOffloadsStrategy(), request.addHeader("X-REDIRECT", "TRUE"));
            assertThat(response.status(), equalTo(OK));
        }
    }
}
