/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.transport.api.HostAndPort;

import io.grpc.examples.deadline.Greeter;
import io.grpc.examples.deadline.HelloRequest;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

/**
 * Extends the async "Hello World!" example to demonstrate use of
 * <a href="https://grpc.io/docs/what-is-grpc/core-concepts/#deadlines">gRPC deadlines</a> aka timeout feature.
 * <p>
 * Start the {@link DeadlineServer} first.
 *
 * @see <a href="https://grpc.io/blog/deadlines/">gRPC and Deadlines</a>
 */
public final class DeadlineClient {

    public static void main(String... args) throws Exception {
        GrpcClientBuilder<HostAndPort, InetSocketAddress> builder = GrpcClients.forAddress("localhost", 8080)
                // set the default timeout for completion of gRPC calls made using this client to 1 minute
                .defaultTimeout(ofMinutes(1));
        try (Greeter.GreeterClient client = builder.build(new Greeter.ClientFactory())) {
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(2);

            // Make a request using default timeout (this will succeed)
            client.sayHello(HelloRequest.newBuilder().setName("DefaultTimeout").build())
                    .afterFinally(responseProcessedLatch::countDown)
                    .afterOnError(System.err::println)
                    .subscribe(System.out::println);

            // Set the timeout for completion of this gRPC call to 3 seconds (this will timeout)
            client.sayHello(new DefaultGrpcClientMetadata(ofSeconds(3)),
                            HelloRequest.newBuilder().setName("3SecondTimeout").build())
                    .afterFinally(responseProcessedLatch::countDown)
                    .afterOnError(System.err::println)
                    .subscribe(System.out::println);

            // block until responses are complete and both afterFinally() have been called
            responseProcessedLatch.await();
        }
    }
}
