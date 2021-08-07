/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.compression;

import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;
import io.servicetalk.grpc.netty.GrpcServers;

import io.grpc.examples.compression.Greeter;
import io.grpc.examples.compression.Greeter.GreeterService;
import io.grpc.examples.compression.HelloReply;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.deflateDefault;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.gzipDefault;
import static java.util.Arrays.asList;

/**
 * A simple extension of the gRPC "Hello World" example which demonstrates
 * compression of the request and response bodies.
 */
public class CompressionExampleServer {
    public static void main(String... args) throws Exception {
        GrpcServers.forPort(8080)
                .listenAndAwait(new Greeter.ServiceFactory.Builder()
                        .bufferDecoderGroup(new BufferDecoderGroupBuilder()
                                .add(gzipDefault(), true)
                                .add(deflateDefault(), true)
                                .add(identityEncoder(), false).build())
                        .bufferEncoders(asList(gzipDefault(), deflateDefault(), identityEncoder()))
                        .addService((GreeterService) (ctx, request) ->
                                succeeded(HelloReply.newBuilder().setMessage("Hello " + request.getName()).build()))
                        .build())
                .awaitShutdown();
    }
}
