/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.ConnectAndHttpExecutionStrategy;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.IoThreadFactory;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionFactoryOffloadingTest {

    @SuppressWarnings("unused")
    static Stream<Arguments> testCases() {
        return Stream.of(
                Arguments.of(false, HttpExecutionStrategies.anyStrategy()),
                Arguments.of(false, HttpExecutionStrategies.offloadAll()),
                Arguments.of(true, HttpExecutionStrategies.anyStrategy()),
                Arguments.of(true, HttpExecutionStrategies.offloadAll())
        );
    }

    @ParameterizedTest(name = "offload={0} httpStrategy={1}")
    @MethodSource("testCases")
    void testFactoryOffloading(boolean offload, HttpExecutionStrategy httpStrategy) throws Exception {
        AtomicReference<Thread> factoryThread = new AtomicReference<>();

        Thread appThread = Thread.currentThread();

        try (ServerContext server = HttpServers.forPort(0)
                .listenAndAwait(this::helloWorld)) {
            SocketAddress serverAddress = server.listenAddress();

            ConnectionFactoryFilter<SocketAddress, FilterableStreamingHttpConnection> factory =
                    ConnectionFactoryFilter.withStrategy(original ->
                                    new ConnectionFactory<SocketAddress, FilterableStreamingHttpConnection>() {
                                private final ListenableAsyncCloseable close = emptyAsyncCloseable();

                                @Override
                                public Single<FilterableStreamingHttpConnection> newConnection(
                                        final SocketAddress socketAddress, @Nullable final TransportObserver observer) {
                                    factoryThread.set(Thread.currentThread());
                                    return original.newConnection(socketAddress, observer);
                                }

                                @Override
                                public Completable onClose() {
                                    return close.onClose();
                                }

                                @Override
                                public Completable closeAsync() {
                                    return close.closeAsync();
                                }

                                @Override
                                public Completable closeAsyncGracefully() {
                                    return close.closeAsyncGracefully();
                                }
                            },
                            new ConnectAndHttpExecutionStrategy(offload ?
                                    ConnectExecutionStrategy.offload() : ConnectExecutionStrategy.anyStrategy(),
                                    httpStrategy));

            try (HttpClient client = HttpClients.forResolvedAddress(serverAddress)
                    .appendConnectionFactoryFilter(factory)
                    .build()) {
                assertThat(client.executionContext().executionStrategy().missing(httpStrategy),
                        is(HttpExecutionStrategies.anyStrategy()));
                Single<HttpResponse> single = client.request(client.get("/sayHello"));
                HttpResponse response = single.toFuture().get();
                assertThat("unexpected status", response.status(), is(HttpResponseStatus.OK));
            }
        }
        assertTrue((offload && !IoThreadFactory.IoThread.isIoThread(factoryThread.get())) ||
                        (!offload && factoryThread.get() == appThread),
                "incorrect offloading, offload=" + offload + " thread=" + factoryThread.get());
    }

    private Single<HttpResponse> helloWorld(HttpServiceContext ctx,
                                            HttpRequest request,
                                            HttpResponseFactory responseFactory) {
        return succeeded(responseFactory.ok().payloadBody("Hello World!", textSerializerUtf8()));
    }
}
