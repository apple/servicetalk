/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.context.async;

import io.servicetalk.examples.grpc.context.HttpContextFilters;
import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcClientMetadata;
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.http.api.HttpMetaData;

import io.grpc.examples.helloworld.Greeter.ClientFactory;
import io.grpc.examples.helloworld.Greeter.GreeterClient;
import io.grpc.examples.helloworld.HelloRequest;

/**
 * This example demonstrates how {@link GrpcClientMetadata#requestContext() request} and
 * {@link GrpcClientMetadata#responseContext() response} context can be used to access {@link HttpMetaData}.
 * <p>
 * Start the {@link RequestResponseContextServer} first.
 */
public final class RequestResponseContextClient {
    public static void main(String... args) throws Exception {
        try (GreeterClient client = GrpcClients.forAddress("localhost", 8080)
                .initializeHttp(httpBuilder -> httpBuilder.appendClientFilter(HttpContextFilters.clientFilter()))
                .build(new ClientFactory())) {

            // GrpcClientMetadata opens access to request and response context:
            GrpcClientMetadata metadata = new DefaultGrpcClientMetadata();
            metadata.requestContext().put(HttpContextFilters.USER_ID_KEY, "xyz");
            client.sayHello(metadata, HelloRequest.newBuilder().setName("World").build())
                    .whenOnSuccess(reply -> {
                        System.out.print(reply);
                        System.out.println("Response user-id: " +
                                metadata.responseContext().get(HttpContextFilters.USER_ID_KEY));
                    })
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}
