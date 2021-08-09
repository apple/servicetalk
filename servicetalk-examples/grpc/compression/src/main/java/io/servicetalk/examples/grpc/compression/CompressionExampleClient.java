/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;
import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.netty.GrpcClients;

import io.grpc.examples.compression.Greeter.ClientFactory;
import io.grpc.examples.compression.Greeter.GreeterClient;
import io.grpc.examples.compression.HelloReply;
import io.grpc.examples.compression.HelloRequest;

import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.deflateDefault;

/**
 * Extends the async "Hello World" example to include support for compression of the server response.
 */
public final class CompressionExampleClient {
    public static void main(String... args) throws Exception {
        try (GreeterClient client = GrpcClients.forAddress("localhost", 8080).build(new ClientFactory()
                .bufferDecoderGroup(new BufferDecoderGroupBuilder()
                        // For the purposes of this example we disable GZip compression and use the
                        // server's second choice (deflate) to demonstrate that negotiation of compression algorithm is
                        // handled correctly.
                        // .add(NettyBufferEncoders.gzipDefault(), true)
                        .add(deflateDefault(), true)
                        .add(identityEncoder(), false).build()))) {
            // This request is sent with the request being uncompressed. The response may
            // be compressed because the ClientFactory will include the encodings we
            // support as a request header.
            Single<HelloReply> respSingle1 = client.sayHello(HelloRequest.newBuilder().setName("NoMeta").build())
                    .whenOnSuccess(System.out::println);

            // This request uses a different overload of the "sayHello" method that allows
            // providing request metadata and we use it to request compression of the request.
            Single<HelloReply> respSingle2 = client.sayHello(new DefaultGrpcClientMetadata(deflateDefault()),
                    HelloRequest.newBuilder().setName("WithMeta").build())
                    .whenOnSuccess(System.out::println);

            // Issue the requests sequentially with concat.
            respSingle1.concat(respSingle2)
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}
