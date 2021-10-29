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
package io.servicetalk.examples.grpc.strategies;

import io.servicetalk.grpc.netty.GrpcClients;

import io.grpc.examples.strategies.Greeter;
import io.grpc.examples.strategies.HelloRequest;

import java.util.stream.IntStream;

/**
 * Extends the async "Hello World" example to demonstrate support for alternative execution strategies and executors.
 * <p>
 * Start the {@link ExecutionStrategyServer} first.
 */
public final class ExecutionStrategyClient {
    private static void sayHello(int port) throws Exception {
        try (Greeter.GreeterClient client =
                     GrpcClients.forAddress("localhost", port).build(new Greeter.ClientFactory())) {
            client.sayHello(HelloRequest.newBuilder().setName("World").build())
                    .whenOnSuccess(reply -> System.out.println(port + " : " + reply))
                    // This example is demonstrating asynchronous execution, but needs to prevent the main thread from
                    // exiting before the response has been processed. This isn't typical usage for an asynchronous API
                    // but is useful for demonstration purposes.
                    .toFuture().get();
        }
    }

    public static void main(String... args) {
        // Execute the same client request for each of the server variations.
        IntStream.rangeClosed(8080, 8088).forEach(port -> {
            try {
                ExecutionStrategyClient.sayHello(port);
            } catch (Throwable all) {
                System.err.println("\nUnexpected throwable for port: " + port);
                all.printStackTrace(System.err);
            }
        });
    }
}
