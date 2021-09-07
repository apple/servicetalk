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
package io.servicetalk.grpc.netty;

import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.IoThreadFactory;

import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.Greeter.GreeterService;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.netty.GrpcClients.forResolvedAddress;
import static io.servicetalk.grpc.netty.GrpcServers.forAddress;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.newSocketAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GrpcUdsTest {
    @Nullable
    private static IoExecutor ioExecutor;

    @BeforeAll
    static void beforeClass() {
        ioExecutor = createIoExecutor(new IoThreadFactory("io-executor"));
    }

    @AfterAll
    static void afterClass() throws ExecutionException, InterruptedException {
        ioExecutor.closeAsync().toFuture().get();
    }

    @Test
    void udsRoundTrip() throws Exception {
        Assumptions.assumeTrue(ioExecutor.isUnixDomainSocketSupported());
        String greetingPrefix = "Hello ";
        String name = "foo";
        String expectedResponse = greetingPrefix + name;
        try (ServerContext serverContext = forAddress(newSocketAddress())
                .ioExecutor(ioExecutor)
                .listenAndAwait((GreeterService) (ctx, request) ->
                        succeeded(HelloReply.newBuilder().setMessage(greetingPrefix + request.getName()).build()));
             BlockingGreeterClient client = forResolvedAddress(serverContext.listenAddress())
                     .buildBlocking(new ClientFactory())) {
            assertEquals(expectedResponse,
                                    client.sayHello(HelloRequest.newBuilder().setName(name).build()).getMessage());
        }
    }
}
