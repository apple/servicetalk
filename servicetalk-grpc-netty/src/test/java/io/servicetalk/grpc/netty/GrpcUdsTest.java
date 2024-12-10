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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;

import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.Greeter.GreeterService;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.grpc.netty.GrpcClients.forResolvedAddress;
import static io.servicetalk.grpc.netty.GrpcServers.forAddress;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.newSocketAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GrpcUdsTest {
    @Nullable
    private static IoExecutor ioExecutor;

    @BeforeAll
    static void beforeClass() {
        ioExecutor = createIoExecutor("io-executor");
    }

    @AfterAll
    static void afterClass() throws ExecutionException, InterruptedException {
        ioExecutor.closeAsync().toFuture().get();
    }

    @Test
    void udsRoundTrip() throws Exception {
        assumeTrue(ioExecutor.isUnixDomainSocketSupported());
        String greetingPrefix = "Hello ";
        String name = "foo";
        try (ServerContext serverContext = forAddress(newSocketAddress())
                .initializeHttp(builder -> builder.ioExecutor(ioExecutor))
                .listenAndAwait(new GreeterService() {
                    @Override
                    public Single<HelloReply> sayHello(GrpcServiceContext ctx, HelloRequest request) {
                        return succeeded(HelloReply.newBuilder()
                                .setMessage(greetingPrefix + request.getName()).build());
                    }
                });
             BlockingGreeterClient client = forResolvedAddress(serverContext.listenAddress())
                     .buildBlocking(new ClientFactory())) {
            HelloRequest request = HelloRequest.newBuilder().setName(name).build();
            HelloReply response = client.sayHello(request);
            assertThat(response.getMessage(), is(equalTo(greetingPrefix + name)));
        }
    }
}
