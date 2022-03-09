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

import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.DelegatingHttpServerBuilder;
import io.servicetalk.http.api.DelegatingMultiAddressHttpClientBuilder;
import io.servicetalk.http.api.DelegatingSingleAddressHttpClientBuilder;
import io.servicetalk.http.api.HttpProviders.HttpServerBuilderProvider;
import io.servicetalk.http.api.HttpProviders.MultiAddressHttpClientBuilderProvider;
import io.servicetalk.http.api.HttpProviders.SingleAddressHttpClientBuilderProvider;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.MultiAddressHttpClientBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopConnectionObserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class HttpProvidersTest {

    @BeforeEach
    void reset() {
        TestHttpServerBuilderProvider.reset();
        TestSingleAddressHttpClientBuilderProvider.reset();
        TestMultiAddressHttpClientBuilderProvider.reset();
    }

    @Test
    void testNoProvidersForAddress() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait(new TestServiceStreaming());
             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .buildBlocking()) {
            HttpResponse response = client.request(client.get(SVC_ECHO));
            assertThat(response.status(), is(OK));
        }
        assertThat(TestHttpServerBuilderProvider.BUILD_COUNTER.get(), is(0));
        assertThat(TestHttpServerBuilderProvider.CONNECTION_COUNTER.get(), is(0));
        assertThat(TestSingleAddressHttpClientBuilderProvider.BUILD_COUNTER.get(), is(0));
        assertThat(TestSingleAddressHttpClientBuilderProvider.CONNECTION_COUNTER.get(), is(0));
    }

    @Test
    void testHttpServerBuilderProvider() throws Exception {
        final InetSocketAddress serverAddress = localAddress(0);
        TestHttpServerBuilderProvider.MODIFY_FOR_ADDRESS.set(serverAddress);
        try (ServerContext serverContext = HttpServers.forAddress(serverAddress)
                .listenStreamingAndAwait(new TestServiceStreaming())) {
            assertThat(TestHttpServerBuilderProvider.BUILD_COUNTER.get(), is(1));
            try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .buildBlocking()) {
                HttpResponse response = client.request(client.get(SVC_ECHO));
                assertThat(response.status(), is(OK));
                assertThat(TestHttpServerBuilderProvider.CONNECTION_COUNTER.get(), is(1));
            }
        }
    }

    @Test
    void testSingleAddressHttpClientBuilderProviderWithHostAndPort() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait(new TestServiceStreaming())) {
            HostAndPort serverAddress = serverHostAndPort(serverContext);
            TestSingleAddressHttpClientBuilderProvider.MODIFY_FOR_ADDRESS.set(serverAddress);
            try (BlockingHttpClient client = HttpClients.forSingleAddress(serverAddress).buildBlocking()) {
                assertThat(TestSingleAddressHttpClientBuilderProvider.BUILD_COUNTER.get(), is(1));
                HttpResponse response = client.request(client.get(SVC_ECHO));
                assertThat(response.status(), is(OK));
                assertThat(TestSingleAddressHttpClientBuilderProvider.CONNECTION_COUNTER.get(), is(1));
            }
        }
    }

    @Test
    void testSingleAddressHttpClientBuilderProviderForResolvedAddress() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait(new TestServiceStreaming())) {
            SocketAddress serverAddress = serverContext.listenAddress();
            TestSingleAddressHttpClientBuilderProvider.MODIFY_FOR_ADDRESS.set(serverAddress);
            try (BlockingHttpClient client = HttpClients.forResolvedAddress(serverAddress).buildBlocking()) {
                assertThat(TestSingleAddressHttpClientBuilderProvider.BUILD_COUNTER.get(), is(1));
                HttpResponse response = client.request(client.get(SVC_ECHO));
                assertThat(response.status(), is(OK));
                assertThat(TestSingleAddressHttpClientBuilderProvider.CONNECTION_COUNTER.get(), is(1));
            }
        }
    }

    @Test
    void testMultiAddressHttpClientBuilderProvider() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait(new TestServiceStreaming())) {
            HostAndPort serverAddress = serverHostAndPort(serverContext);
            TestSingleAddressHttpClientBuilderProvider.MODIFY_FOR_ADDRESS.set(serverAddress);
            try (BlockingHttpClient client = HttpClients.forMultiAddressUrl().buildBlocking()) {
                assertThat(TestMultiAddressHttpClientBuilderProvider.BUILD_COUNTER.get(), is(1));
                HttpResponse response = client.request(client.get("http://" + serverAddress + SVC_ECHO));
                assertThat(response.status(), is(OK));
                assertThat(TestSingleAddressHttpClientBuilderProvider.BUILD_COUNTER.get(), is(1));
                assertThat(TestSingleAddressHttpClientBuilderProvider.CONNECTION_COUNTER.get(), is(1));
            }
        }
    }

    public static final class TestHttpServerBuilderProvider implements HttpServerBuilderProvider {

        static final AtomicReference<SocketAddress> MODIFY_FOR_ADDRESS = new AtomicReference<>();
        static final AtomicInteger BUILD_COUNTER = new AtomicInteger();
        static final AtomicInteger CONNECTION_COUNTER = new AtomicInteger();

        static void reset() {
            MODIFY_FOR_ADDRESS.set(null);
            BUILD_COUNTER.set(0);
            CONNECTION_COUNTER.set(0);
        }

        @Override
        public HttpServerBuilder newBuilder(SocketAddress address, HttpServerBuilder builder) {
            if (address.equals(MODIFY_FOR_ADDRESS.get())) {
                return new DelegatingHttpServerBuilder(
                        builder.transportObserver(transportObserver(CONNECTION_COUNTER))) {

                    @Override
                    public HttpServerContext listenStreamingAndAwait(StreamingHttpService service)
                            throws Exception {
                        BUILD_COUNTER.incrementAndGet();
                        return delegate().listenStreamingAndAwait(service);
                    }
                };
            }
            return builder;
        }
    }

    public static final class TestSingleAddressHttpClientBuilderProvider
            implements SingleAddressHttpClientBuilderProvider {

        static final AtomicReference<Object> MODIFY_FOR_ADDRESS = new AtomicReference<>();
        static final AtomicInteger BUILD_COUNTER = new AtomicInteger();
        static final AtomicInteger CONNECTION_COUNTER = new AtomicInteger();

        static void reset() {
            MODIFY_FOR_ADDRESS.set(null);
            BUILD_COUNTER.set(0);
            CONNECTION_COUNTER.set(0);
        }

        @Override
        public <U, R> SingleAddressHttpClientBuilder<U, R> newBuilder(U address,
                                                                      SingleAddressHttpClientBuilder<U, R> builder) {
            if (address.equals(MODIFY_FOR_ADDRESS.get())) {
                // Test that users can either modify the existing filter or wrap it for additional logic:
                return new DelegatingSingleAddressHttpClientBuilder<U, R>(builder.appendConnectionFactoryFilter(
                        new TransportObserverConnectionFactoryFilter<>(transportObserver(CONNECTION_COUNTER)))) {

                    @Override
                    public BlockingHttpClient buildBlocking() {
                        BUILD_COUNTER.incrementAndGet();
                        return super.buildBlocking();
                    }

                    // multi-address and partitioned client builders uses this method:
                    @Override
                    public StreamingHttpClient buildStreaming() {
                        BUILD_COUNTER.incrementAndGet();
                        return super.buildStreaming();
                    }
                };
            }
            return builder;
        }
    }

    public static final class TestMultiAddressHttpClientBuilderProvider
            implements MultiAddressHttpClientBuilderProvider {

        static final AtomicInteger BUILD_COUNTER = new AtomicInteger();

        static void reset() {
            BUILD_COUNTER.set(0);
        }

        @Override
        public <U, R> MultiAddressHttpClientBuilder<U, R> newBuilder(MultiAddressHttpClientBuilder<U, R> builder) {
            return new DelegatingMultiAddressHttpClientBuilder<U, R>(builder) {

                @Override
                public BlockingHttpClient buildBlocking() {
                    BUILD_COUNTER.incrementAndGet();
                    return delegate().buildBlocking();
                }
            };
        }
    }

    private static TransportObserver transportObserver(AtomicInteger counter) {
        return (localAddress, remoteAddress) -> {
            counter.incrementAndGet();
            return NoopConnectionObserver.INSTANCE;
        };
    }
}
