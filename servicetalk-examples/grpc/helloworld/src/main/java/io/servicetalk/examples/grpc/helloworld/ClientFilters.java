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
package io.servicetalk.examples.grpc.helloworld;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.ClientFactory;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.FilterableGreeterClient;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.GreeterClient;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.GreeterClientFilter;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.GreeterClientFilterFactory;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.SayHelloMetadata;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.HelloReply;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.HelloRequest;
import io.servicetalk.grpc.netty.GrpcClients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public final class ClientFilters {

    public static void main(String[] args) throws Exception {
        try (GreeterClient client = GrpcClients.forAddress("localhost", 8080)
                .build(new ClientFactory().appendClientFilter(new MyClientFilter()))) {
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(1);
            client.sayHello(HelloRequest.newBuilder().setName("Foo").build())
                    .whenFinally(responseProcessedLatch::countDown)
                    .subscribe(System.out::println);

            responseProcessedLatch.await();
        }
    }

    private static final class MyClientFilter implements GreeterClientFilterFactory {
        private static final Logger LOGGER = LoggerFactory.getLogger(MyClientFilter.class);

        @Override
        public GreeterClientFilter create(final FilterableGreeterClient filterableClient) {
            return new GreeterClientFilter(filterableClient) {
                @Override
                public Single<HelloReply> sayHello(final SayHelloMetadata metadata, final HelloRequest request) {
                    LOGGER.error("New request to say hello for {}.", request.getName());
                    return super.sayHello(metadata, request);
                }
            };
        }
    }
}
