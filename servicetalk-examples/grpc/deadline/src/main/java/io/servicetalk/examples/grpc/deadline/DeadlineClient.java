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
package io.servicetalk.examples.grpc.deadline;

import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.transport.api.HostAndPort;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.Greeter.GreeterClient;
import io.grpc.examples.helloworld.HelloRequest;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

/**
 * Extends the async "Hello World!" example to add demonstration of
 * <a herf="https://github.com/grpc/grpc/blob/master/examples/protos/helloworld.proto">gRPC hello world example</a>
 * using async ServiceTalk APIS.
 * <p/>
 * Start the {@link DeadlineServer} first.
 */
public final class DeadlineClient {

    public static void main(String... args) throws Exception {
        GrpcClientBuilder<HostAndPort, InetSocketAddress> builder = GrpcClients.forAddress("localhost", 8080)
                // set the default timeout for completion of gRPC calls made using this client to 1 minute
                .defaultTimeout(Duration.ofMinutes(1));
        try (GreeterClient client = builder.build(new ClientFactory())) {
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(1);
            // Set the timeout for completion of this gRPC call to 5 seconds
            Greeter.SayHelloMetadata metadata = new Greeter.SayHelloMetadata(Duration.ofSeconds(15));
            client.sayHello(metadata, HelloRequest.newBuilder().setName("Foo").build())
                    .afterFinally(responseProcessedLatch::countDown)
                    .subscribe(System.out::println);

            // block until response is complete and afterFinally() is called
            responseProcessedLatch.await();
        }
    }
}
