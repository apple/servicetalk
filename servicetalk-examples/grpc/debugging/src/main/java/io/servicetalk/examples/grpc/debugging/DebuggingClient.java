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
package io.servicetalk.examples.grpc.debugging;

import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.http.netty.HttpProtocolConfigs;

import io.grpc.examples.debugging.Greeter.BlockingGreeterClient;
import io.grpc.examples.debugging.Greeter.ClientFactory;
import io.grpc.examples.debugging.HelloReply;
import io.grpc.examples.debugging.HelloRequest;

import static io.servicetalk.logging.api.LogLevel.TRACE;

public final class DebuggingClient {
    public static void main(String[] args) throws Exception {
        try (BlockingGreeterClient client = GrpcClients.forAddress("localhost", 8080)
                .initializeHttp(builder -> builder.enableWireLogging(
                                "servicetalk-examples-wire-logger", TRACE, Boolean.TRUE::booleanValue)
                        .protocols(HttpProtocolConfigs.h2()
                                .enableFrameLogging("servicetalk-examples-h2-frame-logger", TRACE, Boolean.TRUE::booleanValue)
                                .build()))
                .buildBlocking(new ClientFactory())) {
            HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName("World").build());
            System.out.println(reply);
        }
    }
}
