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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class MultiClientTest {

    @Nullable
    private ServerContext serverContext;
    @Nullable
    private GrpcClientBuilder<HostAndPort, InetSocketAddress> clientBuilder;

    final CountingConnectionObserver countingObserver = new CountingConnectionObserver();

    @BeforeEach
    void setUp() throws Exception {
        final TesterProto.Tester.TesterService service1 = new TesterProto.Tester.TesterService() {
            @Override
            public Publisher<TesterProto.TestResponse> testBiDiStream(
                    final GrpcServiceContext ctx, final Publisher<TesterProto.TestRequest> request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Single<TesterProto.TestResponse> testRequestStream(
                    final GrpcServiceContext ctx, final Publisher<TesterProto.TestRequest> request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Publisher<TesterProto.TestResponse> testResponseStream(
                    final GrpcServiceContext ctx, final TesterProto.TestRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Single<TesterProto.TestResponse> test(
                    final GrpcServiceContext ctx, final TesterProto.TestRequest request) {
                return Single.fromSupplier(() -> TesterProto.TestResponse.newBuilder().setMessage("response").build());
            }
        };
        final Greeter.GreeterService service2 = new Greeter.GreeterService() {
            @Override
            public Single<HelloReply> sayHello(final GrpcServiceContext ctx, final HelloRequest request) {
                return Single.fromSupplier(() -> HelloReply.newBuilder().setMessage("response").build());
            }
        };
        serverContext = GrpcServers.forAddress(localAddress(0)).listenAndAwait(service1, service2);

        countingObserver.count.set(0);

        clientBuilder = GrpcClients.forAddress(serverHostAndPort(serverContext)).initializeHttp(builder -> builder
                .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(countingObserver))
        );
    }

    @Test
    void underlyingTransportIsSharedBetweenClients() throws Exception {
        final GrpcClientBuilder.MultiClientBuilder multiClientBuilder = clientBuilder.buildMulti();

        final TesterProto.Tester.BlockingTesterClient testerClient =
                multiClientBuilder.buildBlocking(new TesterProto.Tester.ClientFactory());
        final Greeter.BlockingGreeterClient greeterClient =
                multiClientBuilder.buildBlocking(new Greeter.ClientFactory());

        testerClient.test(TesterProto.TestRequest.newBuilder().build());
        greeterClient.sayHello(HelloRequest.newBuilder().build());

        assertThat(countingObserver.count.get(), equalTo(1));
    }

    private static class CountingConnectionObserver implements TransportObserver {

        private final AtomicInteger count = new AtomicInteger();

        @Override
        public ConnectionObserver onNewConnection(@Nullable final Object localAddress, final Object remoteAddress) {
            count.incrementAndGet();
            return NoopTransportObserver.NoopConnectionObserver.INSTANCE;
        }
    }
}
