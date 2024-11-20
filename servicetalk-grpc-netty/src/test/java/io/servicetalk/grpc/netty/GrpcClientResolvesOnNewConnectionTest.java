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
package io.servicetalk.grpc.netty;

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServerContext;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.transport.api.HostAndPort;

import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.Greeter.GreeterService;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.netty.HttpClients.DiscoveryStrategy.ON_NEW_CONNECTION;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GrpcClientResolvesOnNewConnectionTest {

    @Test
    void forAddress() throws Exception {
        String greetingPrefix = "Hello ";
        String name = "foo";
        try (GrpcServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new GreeterService() {
                    @Override
                    public Single<HelloReply> sayHello(GrpcServiceContext ctx, HelloRequest request) {
                        return succeeded(HelloReply.newBuilder()
                                .setMessage(greetingPrefix + request.getName()).build());
                    }
                });
             // Use "localhost" to demonstrate that the address will be resolved.
             BlockingGreeterClient client = GrpcClients.forAddress("localhost",
                             serverHostAndPort(serverContext).port(), ON_NEW_CONNECTION)
                     .buildBlocking(new ClientFactory())) {
            HelloRequest request = HelloRequest.newBuilder().setName(name).build();
            HelloReply response = client.sayHello(request);
            assertThat(response.getMessage(), is(equalTo(greetingPrefix + name)));
        }
    }

    @Test
    void withCustomSd() throws Exception {
        String greetingPrefix = "Hello ";
        String name = "foo";
        try (GrpcServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait(new GreeterService() {
                    @Override
                    public Single<HelloReply> sayHello(GrpcServiceContext ctx, HelloRequest request) {
                        return succeeded(HelloReply.newBuilder()
                                .setMessage(greetingPrefix + request.getName()).build());
                    }
                })) {
            // Use "localhost" to demonstrate that the address will be resolved.
            HostAndPort hostAndPort = HostAndPort.of("localhost", serverHostAndPort(serverContext).port());
            @SuppressWarnings("unchecked")
            ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> mockSd =
                    mock(ServiceDiscoverer.class);
            when(mockSd.discover(hostAndPort)).thenReturn(Publisher.from(singletonList(
                    new DefaultServiceDiscovererEvent<>((InetSocketAddress) serverContext.listenAddress(),
                            AVAILABLE))));
            try (BlockingGreeterClient client = GrpcClients.forAddress(mockSd,
                            hostAndPort, ON_NEW_CONNECTION)
                         .buildBlocking(new ClientFactory())) {

                HelloRequest request = HelloRequest.newBuilder().setName(name).build();
                HelloReply response = client.sayHello(request);
                assertThat(response.getMessage(), is(equalTo(greetingPrefix + name)));
                verify(mockSd).discover(hostAndPort);
                verifyNoMoreInteractions(mockSd);
            }
        }
    }
}
