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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.netty.GrpcClients;

import io.grpc.examples.deadline.Greeter.ClientFactory;
import io.grpc.examples.deadline.Greeter.GreeterClient;
import io.grpc.examples.deadline.HelloReply;
import io.grpc.examples.deadline.HelloRequest;

import static io.servicetalk.concurrent.api.Single.collectUnorderedDelayError;
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
        try (GreeterClient client = GrpcClients.forAddress("localhost", 8080)
                // set the default timeout for completion of gRPC calls made using this client to 1 minute
                .defaultTimeout(ofMinutes(1)).build(new ClientFactory())) {
            // Make a request using default timeout (this will succeed)
            Single<HelloReply> respSingle1 =
                    client.sayHello(HelloRequest.newBuilder().setName("DefaultTimeout").build())
                    .whenOnError(System.err::println)
                    .whenOnSuccess(System.out::println);

            // Set the timeout for completion of this gRPC call to 3 seconds (this will timeout)
            Single<HelloReply> respSingle2 = client.sayHello(new DefaultGrpcClientMetadata(ofSeconds(3)),
                            HelloRequest.newBuilder().setName("3SecondTimeout").build())
                    .whenOnError(System.err::println)
                    .whenOnSuccess(System.out::println);

            // Issue the requests in parallel.
            collectUnorderedDelayError(respSingle1, respSingle2)
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}
