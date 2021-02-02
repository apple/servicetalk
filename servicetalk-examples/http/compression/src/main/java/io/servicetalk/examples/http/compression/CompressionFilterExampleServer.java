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

import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.api.ContentCodings;
import io.servicetalk.http.api.ContentCodingHttpServiceFilter;
import io.servicetalk.http.netty.HttpServers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;

/**
 * Extends the async "Hello World" sample to add support for compression of
 * request and response payload bodies.
 */
public final class CompressionFilterExampleServer {

    /**
     * Supported encodings for the request. Requests using unsupported encodings will receive an HTTP 415
     * "Unsupported Media Type" response.
     */
    private static final List<ContentCodec> SUPPORTED_REQ_ENCODINGS =
            Collections.unmodifiableList(Arrays.asList(
                    ContentCodings.gzipDefault(),
                    ContentCodings.deflateDefault(),
                    ContentCodings.identity()
            ));

     /**
     * Supported encodings for the response in preferred order. These will be matched against the list of encodings
      * provided by the client to choose a mutually agreeable encoding.
     */
    private static final List<ContentCodec> SUPPORTED_RESP_ENCODINGS =
             Collections.unmodifiableList(Arrays.asList(
                     // For the purposes of this example we disable GZip for the response compression and use the client's second
                     // choice (deflate) to demonstrate that negotiation of compression algorithm is handled correctly.
                     /* ContentCodings.gzipDefault(), */
                     ContentCodings.deflateDefault(),
                     ContentCodings.identity()
             ));

    public static void main(String... args) throws Exception {
        HttpServers.forPort(8080)
                // Adds a content coding service filter that includes the encodings supported for requests and the
                // preferred encodings for responses. Responses will automatically be compressed if the request includes
                // a mutually agreeable compression encoding that the client indicates they will accept and that the
                // server supports.
                .appendServiceFilter(new ContentCodingHttpServiceFilter(SUPPORTED_REQ_ENCODINGS, SUPPORTED_RESP_ENCODINGS))
                .listenAndAwait((ctx, request, responseFactory) -> {
                        String who = request.payloadBody(textDeserializer());
                        return succeeded(responseFactory.ok().payloadBody("Hello " + who +  "!", textSerializer()));
                })
                .awaitShutdown();
    }
}
