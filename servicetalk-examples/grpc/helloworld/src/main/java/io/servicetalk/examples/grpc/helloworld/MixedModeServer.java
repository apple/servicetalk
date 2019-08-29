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

import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.ServiceFactory;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.HelloReply;
import io.servicetalk.grpc.netty.GrpcServers;

import static io.servicetalk.concurrent.api.Single.succeeded;

public class MixedModeServer {

    public static void main(String[] args) throws Exception {
        GrpcServers.forPort(8080)
                .listenAndAwait(new ServiceFactory.Builder()
                        .sayHello((ctx, request) ->
                                succeeded(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build()))
                        .sayHelloToFromMany((ctx, request) ->
                                request.map(req -> HelloReply.newBuilder().setMessage("Hello " + req.getName()).build()))
                        .sayHelloFromManyBlocking((ctx, request) -> {
                            StringBuilder names = new StringBuilder();
                            for (HelloWorldProto.HelloRequest req : request) {
                                if (names.length() != 0) {
                                    names.append(",");
                                }
                                names.append(req.getName());
                            }
                            return HelloReply.newBuilder().setMessage("Hello " + names.toString()).build();
                        })
                        .sayHelloToManyBlocking((ctx, request, responseWriter) -> {
                            for (int i = 0; i < 10; i++) {
                                responseWriter.write(HelloReply.newBuilder().setMessage(request.getName() + ", Hello " + i + ": ")
                                        .build());
                            }
                        })
                        .build())
                .awaitShutdown();
    }
}
