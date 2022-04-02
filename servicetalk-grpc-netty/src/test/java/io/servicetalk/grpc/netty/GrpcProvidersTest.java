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
package io.servicetalk.grpc.netty;

import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.grpc.api.BlockingGrpcClient;
import io.servicetalk.grpc.api.DelegatingGrpcClientBuilder;
import io.servicetalk.grpc.api.DelegatingGrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcBindableService;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcClientFactory;
import io.servicetalk.grpc.api.GrpcProviders.GrpcClientBuilderProvider;
import io.servicetalk.grpc.api.GrpcProviders.GrpcServerBuilderProvider;
import io.servicetalk.grpc.api.GrpcServerBuilder;
import io.servicetalk.grpc.api.GrpcServerContext;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.http.api.HttpProviders.HttpServerBuilderProvider;
import io.servicetalk.http.api.HttpProviders.SingleAddressHttpClientBuilderProvider;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.Greeter.BlockingGreeterService;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
class GrpcProvidersTest {

    @BeforeEach
    void reset() {
        TestGrpcServerBuilderProvider.reset();
        TestGrpcClientBuilderProvider.reset();
    }

    @Test
    void testNoProvidersForAddress() throws Exception {
        try (ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(BlockingGreeterServiceImpl.INSTANCE);
             Greeter.BlockingGreeterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                     .buildBlocking(new ClientFactory())) {
            HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName("foo").build());
            assertThat(reply.getMessage(), is(equalTo("reply to foo")));
        }
    }

    @Test
    void testGrpcServerBuilderProvider() throws Exception {
        final InetSocketAddress serverAddress = localAddress(0);
        TestGrpcServerBuilderProvider.MODIFY_FOR_ADDRESS.set(serverAddress);
        try (ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(BlockingGreeterServiceImpl.INSTANCE)) {
            assertThat(TestGrpcServerBuilderProvider.BUILD_COUNTER.get(), is(1));
            try (Greeter.BlockingGreeterClient client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                    .buildBlocking(new ClientFactory())) {
                HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName("foo").build());
                assertThat(reply.getMessage(), is(equalTo("reply to foo")));
                assertThat(TestGrpcServerBuilderProvider.CONNECTION_COUNTER.get(), is(1));
            }
        }
    }

    @Test
    void testGrpcClientBuilderProvider() throws Exception {
        try (ServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(BlockingGreeterServiceImpl.INSTANCE)) {
            HostAndPort serverAddress = serverHostAndPort(serverContext);
            TestGrpcClientBuilderProvider.MODIFY_FOR_ADDRESS.set(serverAddress);
            try (Greeter.BlockingGreeterClient client = GrpcClients.forAddress(serverAddress)
                    .buildBlocking(new ClientFactory())) {
                assertThat(TestGrpcClientBuilderProvider.BUILD_COUNTER.get(), is(1));
                HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName("foo").build());
                assertThat(reply.getMessage(), is(equalTo("reply to foo")));
                assertThat(TestGrpcClientBuilderProvider.CONNECTION_COUNTER.get(), is(1));
            }
        }
    }

    private static final class BlockingGreeterServiceImpl implements BlockingGreeterService {

        static final BlockingGreeterService INSTANCE = new BlockingGreeterServiceImpl();

        @Override
        public HelloReply sayHello(GrpcServiceContext ctx, HelloRequest request) {
            return HelloReply.newBuilder().setMessage("reply to " + request.getName()).build();
        }
    }

    public static final class TestGrpcServerBuilderProvider implements GrpcServerBuilderProvider,
                                                                       HttpServerBuilderProvider {

        static final AtomicReference<SocketAddress> MODIFY_FOR_ADDRESS = new AtomicReference<>();
        static final AtomicInteger BUILD_COUNTER = new AtomicInteger();
        static final AtomicInteger CONNECTION_COUNTER = new AtomicInteger();

        static void reset() {
            MODIFY_FOR_ADDRESS.set(null);
            BUILD_COUNTER.set(0);
            CONNECTION_COUNTER.set(0);
        }

        @Override
        public GrpcServerBuilder newBuilder(SocketAddress address, GrpcServerBuilder builder) {
            if (address.equals(MODIFY_FOR_ADDRESS.get())) {
                return new DelegatingGrpcServerBuilder(builder) {

                    @Override
                    public GrpcServerContext listenAndAwait(GrpcBindableService<?>... services) throws Exception {
                        BUILD_COUNTER.incrementAndGet();
                        return delegate().listenAndAwait(services);
                    }
                };
            }
            return builder;
        }

        @Override
        public HttpServerBuilder newBuilder(final SocketAddress address, final HttpServerBuilder builder) {
            if (address.equals(MODIFY_FOR_ADDRESS.get())) {
                return builder.transportObserver(transportObserver(CONNECTION_COUNTER));
            }
            return builder;
        }
    }

    public static final class TestGrpcClientBuilderProvider implements GrpcClientBuilderProvider,
                                                                       SingleAddressHttpClientBuilderProvider {

        static final AtomicReference<Object> MODIFY_FOR_ADDRESS = new AtomicReference<>();
        static final AtomicInteger BUILD_COUNTER = new AtomicInteger();
        static final AtomicInteger CONNECTION_COUNTER = new AtomicInteger();

        static void reset() {
            MODIFY_FOR_ADDRESS.set(null);
            BUILD_COUNTER.set(0);
            CONNECTION_COUNTER.set(0);
        }

        @Override
        public <U, R> GrpcClientBuilder<U, R> newBuilder(U address, GrpcClientBuilder<U, R> builder) {
            if (address.equals(MODIFY_FOR_ADDRESS.get())) {
                return new DelegatingGrpcClientBuilder<U, R>(builder) {

                    @Override
                    public <BlockingClient extends BlockingGrpcClient<?>> BlockingClient buildBlocking(
                            GrpcClientFactory<?, BlockingClient> clientFactory) {
                        BUILD_COUNTER.incrementAndGet();
                        return delegate().buildBlocking(clientFactory);
                    }
                };
            }
            return builder;
        }

        @Override
        public <U, R> SingleAddressHttpClientBuilder<U, R> newBuilder(U address,
                                                                      SingleAddressHttpClientBuilder<U, R> builder) {
            if (address.equals(MODIFY_FOR_ADDRESS.get())) {
                return builder.appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                        transportObserver(CONNECTION_COUNTER)));
            }
            return builder;
        }
    }

    private static TransportObserver transportObserver(AtomicInteger counter) {
        return (localAddress, remoteAddress) -> {
            counter.incrementAndGet();
            return NoopTransportObserver.NoopConnectionObserver.INSTANCE;
        };
    }
}
