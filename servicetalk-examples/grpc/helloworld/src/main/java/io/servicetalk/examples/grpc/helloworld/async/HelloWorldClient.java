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
package io.servicetalk.examples.grpc.helloworld.async;

import io.servicetalk.grpc.netty.GrpcClients;

import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.Greeter.GreeterClient;
import io.grpc.examples.helloworld.HelloRequest;

/**
 * Implementation of the
 * <a href="https://github.com/grpc/grpc/blob/master/examples/protos/helloworld.proto">gRPC hello world example</a>
 * using async ServiceTalk APIS.
 * <p>
 * Start the {@link HelloWorldServer} first.
 */
public final class HelloWorldClient {
    public static void main(String... args) throws Exception {
        try (GreeterClient client = GrpcClients.forAddress("localhost", 8080).build(new ClientFactory())) {
            client.sayHello(HelloRequest.newBuilder().setName("World").build())
                    .whenOnSuccess(System.out::println)
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}
