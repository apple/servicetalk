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
package io.servicetalk.examples.grpc.errors;

import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.grpc.netty.GrpcClients;

import io.grpc.examples.errors.Greeter;
import io.grpc.examples.errors.Greeter.BlockingGreeterClient;
import io.grpc.examples.errors.HelloRequest;

/**
 * Extends the blocking "Hello World" example to include support for application error propagation.
 */
public final class ErrorExampleClient {
    public static void main(String... args) throws Exception {
        try (BlockingGreeterClient client = GrpcClients.forAddress("localhost", 8080)
                .buildBlocking(new Greeter.ClientFactory())) {
            try {
                System.out.println(client.sayHello(HelloRequest.newBuilder().setName("Foo").build()));
            } catch (GrpcStatusException e) {
                System.err.println("Expected error stack trace:");
                e.printStackTrace();
                System.err.println("Expected error application status:");
                System.err.println(e.applicationStatus());
            }
        }
    }
}
