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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_DIRECT_RO_ALLOCATOR;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.PREFER_HEAP_RO_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpApiConversions.toStreamingHttpService;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@RunWith(Parameterized.class)
public class SupportedBufferAllocatorsTest extends AbstractNettyHttpServerTest {

    private final HttpProtocol protocol;
    private final BufferAllocator allocator;

    public SupportedBufferAllocatorsTest(HttpProtocol protocol, BufferAllocator allocator) {
        super(CACHED, CACHED_SERVER);
        this.protocol = protocol;
        protocol(protocol.config);
        this.allocator = allocator;
        service((toStreamingHttpService((BlockingHttpService) (ctx, request, responseFactory) -> responseFactory.ok()
                .payloadBody(allocator.fromAscii(request.payloadBody().toString(US_ASCII))),
                strategy -> strategy)).adaptor());
    }

    @Parameterized.Parameters(name = "{index}: protocol={0}, allocator={1}")
    public static Collection<Object[]> data() {
        return asList(
                new Object[]{HTTP_1, PREFER_HEAP_ALLOCATOR},
                new Object[]{HTTP_1, PREFER_DIRECT_ALLOCATOR},
                new Object[]{HTTP_1, PREFER_HEAP_RO_ALLOCATOR},
                new Object[]{HTTP_1, PREFER_DIRECT_RO_ALLOCATOR},
                new Object[]{HTTP_2, PREFER_HEAP_ALLOCATOR},
                new Object[]{HTTP_2, PREFER_DIRECT_ALLOCATOR},
                new Object[]{HTTP_2, PREFER_HEAP_RO_ALLOCATOR},
                new Object[]{HTTP_2, PREFER_DIRECT_RO_ALLOCATOR});
    }

    @Test
    public void test() throws Exception {
        String payload = "Hello ServiceTalk";
        StreamingHttpRequest request = streamingHttpConnection().post("/echo")
                .payloadBody(from(allocator.fromAscii(payload)));
        StreamingHttpResponse response = makeRequest(request);
        assertResponse(response, protocol.version, HttpResponseStatus.OK, singletonList(payload));
    }
}
