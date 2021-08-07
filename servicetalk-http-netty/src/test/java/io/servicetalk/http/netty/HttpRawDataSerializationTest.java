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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpStreamingSerializer;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;

import java.util.Objects;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpSerializers.bytesStreamingSerializer;
import static io.servicetalk.http.api.HttpSerializers.stringStreamingSerializer;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * This test simulates pre-existing content in string, and we know the content type a-prior. This maybe useful when
 * calling 3rd party libraries that provide json/xml content in String.
 */
class HttpRawDataSerializationTest {
    @Test
    void stringSerialization() throws Exception {
        final String contentType = "foo";
        final String[] content = {"hello", "world", "!"};
        int expectedContentLength = 0;
        for (final String s : content) {
            expectedContentLength += s.length();
        }

        runTest(content, contentType, expectedContentLength, stringStreamingSerializer(UTF_8,
                headers -> headers.set(CONTENT_TYPE, contentType)));
    }

    @Test
    void bytesSerialization() throws Exception {
        final String contentType = "foo";
        final byte[][] content = {"hello".getBytes(UTF_8), "world".getBytes(UTF_8), "!".getBytes(UTF_8)};
        int expectedContentLength = 0;
        for (final byte[] s : content) {
            expectedContentLength += s.length;
        }

        runTest(content, contentType, expectedContentLength, bytesStreamingSerializer(
                headers -> headers.set(CONTENT_TYPE, contentType)));
    }

    private static <T> void runTest(T[] content, String contentType, int expectedContentLength,
                                    HttpStreamingSerializer<T> streamingSerializer) throws Exception {
        try (ServerContext srv = HttpServers.forAddress(localAddress(0))
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(from(content), streamingSerializer)));
             BlockingHttpClient clt = HttpClients.forSingleAddress(serverHostAndPort(srv)).buildBlocking()) {
            HttpResponse resp = clt.request(clt.get("/hello"));
            assertThat(Objects.toString(resp.headers().get(CONTENT_TYPE)), is(contentType));
            assertThat(resp.payloadBody().readableBytes(), is(expectedContentLength));
        }
    }
}
