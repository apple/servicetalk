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

import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpContextKeys;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.ReservedHttpConnection;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.transport.netty.internal.CloseUtils.safeClose;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReserveConnectionTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private HttpServerContext serverContext;
    private BlockingHttpClient httpClient;
    private final AtomicInteger createdConnections = new AtomicInteger(0);

    private void setup(HttpProtocol protocol, boolean reject) throws Exception {
        HttpServerBuilder serverBuilder = BuilderUtils.newServerBuilder(SERVER_CTX, protocol);
        if (reject) {
            serverBuilder.appendEarlyConnectionAcceptor(conn -> Completable.failed(DELIBERATE_EXCEPTION));
        }
        serverContext = serverBuilder.listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());

        httpClient = BuilderUtils.newClientBuilder(serverContext, CLIENT_CTX, protocol)
                .appendConnectionFactoryFilter(o ->
                        new DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection>(o) {
                            @Override
                            public Single<FilterableStreamingHttpConnection> newConnection(
                                    final InetSocketAddress inetSocketAddress,
                                    @Nullable final ContextMap context,
                                    @Nullable final TransportObserver observer) {
                                return delegate()
                                        .newConnection(inetSocketAddress, context, observer)
                                        .beforeOnSuccess(c -> createdConnections.incrementAndGet());
                            }
                        })
                .buildBlocking();
    }

    @AfterEach
    void cleanup() throws Exception {
        safeClose(httpClient);
        safeClose(serverContext);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void reusesConnectionOnReserve(HttpProtocol protocol) throws Exception {
        setup(protocol, false);
        HttpRequest metaData = httpClient.get("/");

        ReservedBlockingHttpConnection connection = httpClient.reserveConnection(metaData);
        connection.release();
        connection = httpClient.reserveConnection(metaData);
        connection.release();
        connection = httpClient.reserveConnection(metaData);
        connection.release();

        assertThat(createdConnections.get(), is(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void canForceNewConnection(HttpProtocol protocol) throws Exception {
        setup(protocol, false);
        HttpRequest metaData = httpClient.get("/");
        metaData.context().put(HttpContextKeys.HTTP_FORCE_NEW_CONNECTION, true);

        ReservedBlockingHttpConnection connection = httpClient.reserveConnection(metaData);
        connection.release();
        connection = httpClient.reserveConnection(metaData);
        connection.release();
        connection = httpClient.reserveConnection(metaData);
        connection.release();

        assertThat(createdConnections.get(), is(3));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void reservedConnectionClosesOnExceptionCaught(HttpProtocol protocol) throws Exception {
        setup(protocol, true);
        HttpRequest metaData = httpClient.get("/");

        ReservedHttpConnection connection = httpClient.reserveConnection(metaData).asConnection();
        ExecutionException e = assertThrows(ExecutionException.class,
                () -> connection.request(connection.get("/")).toFuture().get());
        assertThat(e.getCause(), is(instanceOf(IOException.class)));
        connection.onClose().toFuture().get();

        // Connection can be closed before it goes back through LB. In this case, selection will fail and retried.
        assertThat(createdConnections.get(), is(greaterThanOrEqualTo(1)));
    }
}
