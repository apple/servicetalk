/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.protocoptions;

import io.servicetalk.grpc.netty.GrpcClients;

import io.grpc.examples.helloworld.GreeterSt;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;

public final class BlockingProtocOptionsClient {

    public static void main(String[] args) throws Exception {
        try (GreeterSt.BlockingGreeterClient client = GrpcClients.forAddress("localhost", 8080)
                .buildBlocking(new GreeterSt.ClientFactory())) {
            HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName("Options").build());
            System.out.println(reply);
        }
    }
}
