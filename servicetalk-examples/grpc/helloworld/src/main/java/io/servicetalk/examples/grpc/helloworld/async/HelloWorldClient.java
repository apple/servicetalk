/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.helloworld.async;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.transport.api.HostAndPort;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.Greeter.GreeterClient;
import io.grpc.examples.helloworld.HelloRequest;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;

/**
 * Implementation of the
 * <a herf="https://github.com/grpc/grpc/blob/master/examples/protos/helloworld.proto">gRPC hello world example</a>
 * using async ServiceTalk APIS.
 * <p/>
 * Start the {@link HelloWorldServer} first.
 */
public final class HelloWorldClient {

    public static void main(String... args) throws Exception {
        GrpcClientBuilder<HostAndPort, InetSocketAddress> builder = GrpcClients.forAddress("localhost", 8080)
                // (optional) set the default timeout for completion of RPC calls
                .defaultTimeout(Duration.ofMinutes(1));
        try (GreeterClient client = builder.build(new ClientFactory())) {
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(1);
            // (optional) set the timeout for completion of this RPC
            Greeter.SayHelloMetadata metadata = new Greeter.SayHelloMetadata(Duration.ofSeconds(10));
            client.sayHello(metadata, HelloRequest.newBuilder().setName("Foo").build())
                    .afterFinally(responseProcessedLatch::countDown)
                    .subscribe(System.out::println);

            responseProcessedLatch.await();
        }
    }
}
