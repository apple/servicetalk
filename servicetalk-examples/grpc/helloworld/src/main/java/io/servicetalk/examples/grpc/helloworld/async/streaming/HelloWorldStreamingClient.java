/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.helloworld.async.streaming;

import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.ClientFactory;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.GreeterClient;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.HelloRequest;
import io.servicetalk.grpc.netty.GrpcClients;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.concurrent.api.Publisher.from;

public class HelloWorldStreamingClient {

    public static void main(String[] args) throws Exception {
        try (GreeterClient client = GrpcClients.forAddress("localhost", 8080).build(new ClientFactory())) {
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(1);
            client.sayHelloToFromMany(from(HelloRequest.newBuilder().setName("Foo 1").build(),
                    HelloRequest.newBuilder().setName("Foo 2").build()))
                    .whenFinally(responseProcessedLatch::countDown)
                    .forEach(System.out::println);

            responseProcessedLatch.await();
        }
    }
}
