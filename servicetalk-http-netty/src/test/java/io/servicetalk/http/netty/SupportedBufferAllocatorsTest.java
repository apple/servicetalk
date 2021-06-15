/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpService;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_DIRECT_RO_ALLOCATOR;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_HEAP_RO_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static java.nio.charset.StandardCharsets.US_ASCII;

class SupportedBufferAllocatorsTest extends AbstractNettyHttpServerTest {

    private BufferAllocator allocator;

    private void setUp(HttpProtocol protocol, BufferAllocator allocator) {
        this.allocator = allocator;
        protocol(protocol.config);
        super.setUp(CACHED, CACHED_SERVER);
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> data() {
        return Stream.of(
            Arguments.of(HTTP_1, PREFER_HEAP_ALLOCATOR),
            Arguments.of(HTTP_1, PREFER_DIRECT_ALLOCATOR),
            Arguments.of(HTTP_1, PREFER_HEAP_RO_ALLOCATOR),
            Arguments.of(HTTP_1, PREFER_DIRECT_RO_ALLOCATOR),
            Arguments.of(HTTP_2, PREFER_HEAP_ALLOCATOR),
            Arguments.of(HTTP_2, PREFER_DIRECT_ALLOCATOR),
            Arguments.of(HTTP_2, PREFER_HEAP_RO_ALLOCATOR),
            Arguments.of(HTTP_2, PREFER_DIRECT_RO_ALLOCATOR));
    }

    @Override
    void service(final StreamingHttpService service) {
        super.service((toStreamingHttpService((BlockingHttpService) (ctx, request, responseFactory) ->
                        responseFactory.ok().payloadBody(allocator.fromAscii(request.payloadBody().toString(US_ASCII))),
                strategy -> strategy)).adaptor());
    }

    @ParameterizedTest(name = "{index}: protocol={0}, allocator={1}")
    @MethodSource("data")
    void test(HttpProtocol protocol, BufferAllocator allocator) throws Exception {
        setUp(protocol, allocator);
        String payload = "Hello ServiceTalk";
        StreamingHttpRequest request = streamingHttpConnection().post("/")
            .payloadBody(from(allocator.fromAscii(payload)));
        StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, protocol.version, OK, payload);
    }
}
