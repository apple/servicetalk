/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.grpc.netty.GrpcServers;

import io.grpc.examples.helloworld.Greeter.GreeterService;
import io.grpc.examples.helloworld.HelloReply;

import static io.servicetalk.concurrent.api.Single.succeeded;

/**
 * Implementation of the
 * <a href="https://github.com/grpc/grpc/blob/master/examples/protos/helloworld.proto">gRPC hello world example</a>
 * using async ServiceTalk APIS.
 * <p>
 * Start this server first and then run the {@link HelloWorldClient}.
 */
public class HelloWorldServer {
    public static void main(String... args) throws Exception {
        GrpcServers.forPort(8080)
                .listenAndAwait((GreeterService) (ctx, request) ->
                        succeeded(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build()))
                .awaitShutdown();
    }
}
