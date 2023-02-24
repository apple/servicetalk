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

import io.servicetalk.grpc.api.GrpcServerContext;

import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.Greeter.GreeterService;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.netty.HttpClients.DiscoveryStrategy.ON_NEW_CONNECTION;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class GrpcClientResolvesOnNewConnectionTest {

    @Test
    void test() throws Exception {
        String greetingPrefix = "Hello ";
        String name = "foo";
        try (GrpcServerContext serverContext = GrpcServers.forAddress(localAddress(0))
                .listenAndAwait((GreeterService) (ctx, request) ->
                        succeeded(HelloReply.newBuilder().setMessage(greetingPrefix + request.getName()).build()));
             // Use "localhost" to demonstrate that the address will be resolved.
             BlockingGreeterClient client = GrpcClients.forAddress("localhost",
                             serverHostAndPort(serverContext).port(), ON_NEW_CONNECTION)
                     .buildBlocking(new ClientFactory())) {
            HelloRequest request = HelloRequest.newBuilder().setName(name).build();
            HelloReply response = client.sayHello(request);
            assertThat(response.getMessage(), is(equalTo(greetingPrefix + name)));
        }
    }
}
