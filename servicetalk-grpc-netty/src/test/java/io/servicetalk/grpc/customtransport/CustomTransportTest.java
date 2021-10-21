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
package io.servicetalk.grpc.customtransport;

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.grpc.api.GrpcBindableService;
import io.servicetalk.grpc.api.GrpcClientCallFactory;
import io.servicetalk.grpc.netty.TesterProto.TestRequest;
import io.servicetalk.grpc.netty.TesterProto.Tester;
import io.servicetalk.grpc.netty.TesterProto.Tester.TesterClient;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.grpc.customtransport.Utils.newResp;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

class CustomTransportTest {
    enum ServiceType {
        ASYNC(new AsyncUserService()), BLOCKING(new BlockingUserService());

        final GrpcBindableService<?> grpcService;

        ServiceType(GrpcBindableService<?> grpcService) {
            this.grpcService = grpcService;
        }
    }

    @ParameterizedTest
    @EnumSource(ServiceType.class)
    void testCustomTransport(final ServiceType serviceType) throws Exception {
        // You can re-use the EventLoopGroup used by your Netty application, we create one to demonstrate its use.
        EventLoopAwareNettyIoExecutor ioExecutor = createIoExecutor("netty-el");
        // This is the Netty channel which is reading the request. See getServiceContext(Channel), depending
        // upon what control you want to give users knowing this may not be necessary.
        Channel c = new EmbeddedChannel();
        try {
            ServerTransport serverTransport = new InMemoryServerTransport(DEFAULT_ALLOCATOR, serviceType.grpcService);

            // Build the client with the custom transport and bridge to server's transport.
            Tester.TesterClient client = new Tester.ClientFactory() {
                @Override
                public TesterClient newClient(final GrpcClientCallFactory factory) {
                    return super.newClient(factory);
                }
            }.newClient(new ClientTransportGrpcCallFactory(
                    // Build the client transport, which just calls the server transport directly.
                    (method, requestMessages) -> serverTransport.handle(c, "clientId", method, requestMessages),
                    ioExecutor.eventLoopGroup()));

            // Test using the client.
            assertThat(client.test(newReq("scalar")).toFuture().get(), is(newResp("hello scalar")));
            assertThat(client.testRequestStream(newReqStream("req")).toFuture().get(),
                    is(newResp("hello reqstream1, reqstream2, ")));
            assertThat(client.testResponseStream(newReq("respStream")).toFuture().get(),
                    contains(newResp("hello respStream1"), newResp("hello respStream2")));
            assertThat(client.testBiDiStream(newReqStream("duplex")).toFuture().get(),
                    contains(newResp("hello duplexstream1"), newResp("hello duplexstream2")));
        } finally {
            c.close();

            ioExecutor.closeAsync().toFuture().get();
        }
    }

    private static Publisher<TestRequest> newReqStream(String prefix) {
        return from(newReq(prefix + "stream1"), newReq(prefix + "stream2"));
    }

    private static TestRequest newReq(String name) {
        return TestRequest.newBuilder().setName(name).build();
    }
}
