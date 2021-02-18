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

import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.api.ContentCodings;
import io.servicetalk.grpc.netty.GrpcClients;

import io.grpc.examples.compression.Greeter;
import io.grpc.examples.compression.Greeter.ClientFactory;
import io.grpc.examples.compression.Greeter.GreeterClient;
import io.grpc.examples.compression.HelloRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Extends the async "Hello World" example to include support for compression of the server response.
 */
public final class CompressionExampleClient {

    /**
     * Encodings supported in preferred order.
     */
    private static final List<ContentCodec> PREFERRED_ENCODINGS =
            Collections.unmodifiableList(Arrays.asList(
                    // For the purposes of this example we disable GZip compression and use the
                    // server's second choice (deflate) to demonstrate that negotiation of compression algorithm is
                    // handled correctly.
                    // ContentCodings.gzipDefault(),
                    ContentCodings.deflateDefault(),
                    ContentCodings.identity()
            ));

    /**
     * Metadata that, when provided to a sayHello, will cause the request to be compressed.
     */
    private static final Greeter.SayHelloMetadata COMPRESS_REQUEST = new Greeter.SayHelloMetadata(ContentCodings.deflateDefault());

    public static void main(String... args) throws Exception {
        try (GreeterClient client = GrpcClients.forAddress("localhost", 8080).build(new ClientFactory()
                // Requests will include 'Message-Accept-Encoding' HTTP header to inform the server which encodings we support.
                .supportedMessageCodings(PREFERRED_ENCODINGS))) {
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(2);

            // This request is sent with the request being uncompressed. The response may
            // be compressed because the ClientFactory will include the encodings we
            // support as a request header.
            client.sayHello(HelloRequest.newBuilder().setName("Foo").build())
                    .afterFinally(responseProcessedLatch::countDown)
                    .subscribe(System.out::println);

            // This request uses a different overload of the "sayHello" method that allows
            // providing request metadata and we use it to request compression of the
            // request.
            client.sayHello(COMPRESS_REQUEST, HelloRequest.newBuilder().setName("Foo").build())
                    .afterFinally(responseProcessedLatch::countDown)
                    .subscribe(System.out::println);

            responseProcessedLatch.await();
        }
    }
}
