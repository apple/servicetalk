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

import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.GrpcServers;

import io.grpc.examples.helloworld.GreeterSt;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;

public class BlockingProtocOptionsServer {

    public static void main(String[] args) throws Exception {
        GrpcServers.forPort(8080)
                .listenAndAwait(new GreeterSt.ServiceFactory(new MyGreeterService()))
                .awaitShutdown();
    }

    private static final class MyGreeterService implements GreeterSt.BlockingGreeterService {
        @Override
        public HelloReply sayHello(final GrpcServiceContext ctx, final HelloRequest request) {
            return HelloReply.newBuilder().setMessage("Hello " + request.getName()).build();
        }
    }
}
