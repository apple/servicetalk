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

import io.servicetalk.grpc.api.GrpcServerContext;
import io.servicetalk.grpc.api.GrpcServiceContext;

import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.Greeter.BlockingGreeterService;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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
}
