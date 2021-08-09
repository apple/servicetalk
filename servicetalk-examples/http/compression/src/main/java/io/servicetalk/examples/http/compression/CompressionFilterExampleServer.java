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

import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;
import io.servicetalk.http.api.ContentEncodingHttpServiceFilter;
import io.servicetalk.http.netty.HttpServers;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.deflateDefault;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.gzipDefault;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static java.util.Arrays.asList;

/**
 * Extends the async "Hello World" sample to add support for compression of request and response payload bodies.
 */
public final class CompressionFilterExampleServer {
    public static void main(String... args) throws Exception {
        HttpServers.forPort(8080)
                .appendServiceFilter(new ContentEncodingHttpServiceFilter(
                        asList(gzipDefault(), deflateDefault(), identityEncoder()),
                        new BufferDecoderGroupBuilder()
                                .add(gzipDefault(), true)
                                .add(deflateDefault(), true)
                                .add(identityEncoder(), false).build()))
                .listenAndAwait((ctx, request, responseFactory) -> {
                        String who = request.payloadBody(textSerializerUtf8());
                        return succeeded(responseFactory.ok().payloadBody("Hello " + who +  "!", textSerializerUtf8()));
                })
                .awaitShutdown();
    }
}
