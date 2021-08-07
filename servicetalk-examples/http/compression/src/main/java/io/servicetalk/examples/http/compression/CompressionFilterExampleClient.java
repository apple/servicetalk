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
package io.servicetalk.examples.http.compression;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;
import io.servicetalk.http.api.ContentEncodingHttpRequesterFilter;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.deflateDefault;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.gzipDefault;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * Extends the async "Hello World" example to include compression of the request and response bodies.
 */
public final class CompressionFilterExampleClient {
    public static void main(String... args) throws Exception {
        try (HttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                // Adds filter that provides compression for the request body when a request sets the encoding.
                // Also sets the accept encoding header for the server's response.
                .appendClientFilter(new ContentEncodingHttpRequesterFilter(new BufferDecoderGroupBuilder()
                        // For the purposes of this example we disable GZip compression and use the
                        // server's second choice (deflate) to demonstrate that negotiation of compression algorithm is
                        // handled correctly.
                        // .add(NettyBufferEncoders.gzipDefault(), true)
                        .add(deflateDefault(), true)
                        .add(identityEncoder(), false).build()))
                .build()) {
            // Make a request with an uncompressed payload.
            HttpRequest request = client.post("/sayHello1")
                    // Request will be sent with no compression, same effect as setting encoding to identity
                    .contentEncoding(identityEncoder())
                    .payloadBody("World1", textSerializerUtf8());
            Single<HttpResponse> respSingle1 = client.request(request)
                    .whenOnSuccess(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textSerializerUtf8()));
                    });

            // Make a request with an gzip compressed payload.
            request = client.post("/sayHello2")
                    // Encode the request using gzip.
                    .contentEncoding(gzipDefault())
                    .payloadBody("World2", textSerializerUtf8());
            Single<HttpResponse> respSingle2 = client.request(request)
                    .whenOnSuccess(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textSerializerUtf8()));
                    });

            // Issue the requests sequentially with concat.
            respSingle1.concat(respSingle2)
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}
