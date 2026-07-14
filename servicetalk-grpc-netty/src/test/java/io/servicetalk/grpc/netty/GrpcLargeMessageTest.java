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
package io.servicetalk.grpc.netty;

import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;
import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcServerContext;
import io.servicetalk.grpc.api.GrpcServiceContext;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.Greeter.BlockingGreeterService;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static io.servicetalk.encoding.netty.NettyBufferEncoders.gzipDefault;
import static io.servicetalk.grpc.netty.GrpcClients.forAddress;
import static io.servicetalk.grpc.netty.GrpcServers.forAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class GrpcLargeMessageTest {

    // Larger than the HTTP-level default aggregated payload limit (4 MiB). gRPC frames its own messages and disables
    // that HTTP default, so a unary round-trip well above it must still succeed (gRPC-specific limits tracked
    // separately). Guards against the HTTP aggregation limit silently leaking into gRPC.
    private static final int PAYLOAD_SIZE = 8 * 1024 * 1024;

    @Test
    void unaryMessageLargerThanHttpAggregationLimitRoundTrips() throws Exception {
        final char[] chars = new char[PAYLOAD_SIZE];
        Arrays.fill(chars, 'x');
        final String large = new String(chars);

        try (GrpcServerContext server = forAddress(localAddress(0)).listenAndAwait(new BlockingGreeterService() {
                    @Override
                    public HelloReply sayHello(GrpcServiceContext ctx, HelloRequest request) {
                        return HelloReply.newBuilder().setMessage(request.getName()).build();
                    }
                });
             BlockingGreeterClient client = forAddress(serverHostAndPort(server))
                     .buildBlocking(new ClientFactory())) {
            HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName(large).build());
            assertThat(reply.getMessage().length(), equalTo(PAYLOAD_SIZE));
        }
    }

    // The compressed request serializer is message-type-specific, so two clients sharing the same request compressor
    // for different message types must each get their own serializer rather than a shared one keyed only by codec.
    @Test
    void twoClientsSharingRequestCompressorForDifferentMessageTypes() throws Exception {
        try (GrpcServerContext greeterServer = forAddress(localAddress(0)).listenAndAwait(
                     new Greeter.ServiceFactory.Builder()
                             .bufferDecoderGroup(new BufferDecoderGroupBuilder().add(gzipDefault()).build())
                             .sayHelloBlocking((ctx, request) ->
                                     HelloReply.newBuilder().setMessage(request.getName()).build())
                             .build());
             BlockingGreeterClient greeterClient = forAddress(serverHostAndPort(greeterServer))
                     .buildBlocking(new ClientFactory());
             GrpcServerContext testerServer = forAddress(localAddress(0)).listenAndAwait(
                     new TesterProto.Tester.ServiceFactory.Builder()
                             .bufferDecoderGroup(new BufferDecoderGroupBuilder().add(gzipDefault()).build())
                             .testBlocking((ctx, request) ->
                                     TesterProto.TestResponse.newBuilder().setMessage(request.getName()).build())
                             .build());
             TesterProto.Tester.BlockingTesterClient testerClient = forAddress(serverHostAndPort(testerServer))
                     .buildBlocking(new TesterProto.Tester.ClientFactory())) {

            HelloReply reply = greeterClient.sayHello(new DefaultGrpcClientMetadata(gzipDefault()),
                    HelloRequest.newBuilder().setName("hello").build());
            assertThat(reply.getMessage(), equalTo("hello"));

            TesterProto.TestResponse response = testerClient.test(new DefaultGrpcClientMetadata(gzipDefault()),
                    TesterProto.TestRequest.newBuilder().setName("world").build());
            assertThat(response.getMessage(), equalTo("world"));
        }
    }
}
