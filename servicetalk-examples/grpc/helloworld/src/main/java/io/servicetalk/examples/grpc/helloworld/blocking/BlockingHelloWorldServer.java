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
package io.servicetalk.examples.grpc.helloworld.blocking;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.BlockingGreeterService;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.Greeter.ServiceFactory;
import io.servicetalk.examples.grpc.helloworld.HelloWorldProto.HelloReply;
import io.servicetalk.grpc.api.GrpcPayloadWriter;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.netty.GrpcServers;
import io.servicetalk.transport.api.ConnectionContext;

public class BlockingHelloWorldServer {

    public static void main(String[] args) throws Exception {
        GrpcServers.forPort(8080)
                .listenAndAwait(new ServiceFactory(new MyGreeterService()))
                .awaitShutdown();
    }

    private static final class MyGreeterService implements BlockingGreeterService {

        @Override
        public HelloReply sayHello(final GrpcServiceContext ctx, final HelloWorldProto.HelloRequest request) {
            return HelloReply.newBuilder().setMessage("Hello " + request.getName()).build();
        }

        @Override
        public void sayHelloToFromMany(final GrpcServiceContext ctx,
                                       final BlockingIterable<HelloWorldProto.HelloRequest> request,
                                       final GrpcPayloadWriter<HelloReply> responseWriter)  throws Exception {
            try {
                for (HelloWorldProto.HelloRequest req : request) {
                    responseWriter.write(HelloReply.newBuilder().setMessage("Hello " + req.getName()).build());
                }
            } finally {
                responseWriter.close();
            }
        }

        @Override
        public void sayHelloToMany(final GrpcServiceContext ctx, final HelloWorldProto.HelloRequest request,
                                   final GrpcPayloadWriter<HelloReply> responseWriter) throws Exception {
            try {
                for (int i = 0; i < 10; i++) {
                    responseWriter.write(HelloReply.newBuilder().setMessage("Hello " + i + ": " + request.getName())
                            .build());
                }
            } finally {
                responseWriter.close();
            }
        }

        @Override
        public HelloReply sayHelloFromMany(final GrpcServiceContext ctx,
                                           final BlockingIterable<HelloWorldProto.HelloRequest> request) {
            StringBuilder names = new StringBuilder();
            for (HelloWorldProto.HelloRequest req : request) {
                if (names.length() != 0) {
                    names.append(",");
                }
                names.append(req.getName());
            }
            return HelloReply.newBuilder().setMessage("Hello " + names.toString()).build();
        }
    }
}
