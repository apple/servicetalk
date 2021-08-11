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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED_SERVER;
import static io.servicetalk.http.netty.TestServiceStreaming.SVC_ECHO;

class PayloadBodyModificationsTest extends AbstractNettyHttpServerTest {

    private static final String CONTENT = "content";

    PayloadBodyModificationsTest() {
        setUp(CACHED, CACHED_SERVER);
    }

    @Test
    void aggregatedSetPayloadBody() throws Exception {
        HttpClient client = streamingHttpClient().asClient();
        HttpRequest request = client.post(SVC_ECHO)
                .payloadBody(client.executionContext().bufferAllocator().fromAscii(CONTENT));
        assertResponse(makeRequest(request.toStreamingRequest()), request.version(), OK, CONTENT);
    }

    @Test
    void aggregatedExpandOriginalBuffer() throws Exception {
        HttpRequest request = streamingHttpClient().asClient().post(SVC_ECHO);
        Buffer payload = request.payloadBody();
        payload.writeAscii(CONTENT);
        assertResponse(makeRequest(request.toStreamingRequest()), request.version(), OK, CONTENT);
    }

    @Test
    void aggregatedExpandOriginalBufferAfterTypeConversion() throws Exception {
        HttpRequest request = streamingHttpClient().asClient().post(SVC_ECHO);
        Buffer payload = request.payloadBody();
        StreamingHttpRequest streamingRequest = request.toStreamingRequest();
        payload.writeAscii(CONTENT);
        assertResponse(makeRequest(streamingRequest), request.version(), OK, CONTENT);
    }

    @Test
    void aggregatedTakeAndExpandOriginalBufferAfterTypeConversion() throws Exception {
        HttpRequest request = streamingHttpClient().asClient().post(SVC_ECHO);
        StreamingHttpRequest streamingRequest = request.toStreamingRequest();
        // Too late to take the buffer from the original aggregated request object, modifications won't be visible:
        Buffer payload = request.payloadBody();
        payload.writeAscii(CONTENT);
        assertResponse(makeRequest(streamingRequest), request.version(), OK, 0);
    }

    @Test
    void streamingSetPayloadBody() throws Exception {
        StreamingHttpClient client = streamingHttpClient();
        StreamingHttpRequest request = client.post(SVC_ECHO)
                .payloadBody(from(client.executionContext().bufferAllocator().fromAscii(CONTENT)));
        assertResponse(makeRequest(request), request.version(), OK, CONTENT);
    }

    @Test
    void streamingSetPayloadBodyWithSerializer() throws Exception {
        StreamingHttpRequest request = streamingHttpClient().post(SVC_ECHO)
                .payloadBody(from(CONTENT), appSerializerUtf8FixLen());
        assertSerializedResponse(makeRequest(request), request.version(), OK, CONTENT);
    }

    @Test
    void streamingExpandOriginalBuffer() throws Exception {
        StreamingHttpClient client = streamingHttpClient();
        Buffer buffer = client.executionContext().bufferAllocator().newBuffer(0, false);
        StreamingHttpRequest request = client.post(SVC_ECHO).payloadBody(from(buffer));
        buffer.writeAscii(CONTENT);
        assertResponse(makeRequest(request), request.version(), OK, CONTENT);
    }

    @Test
    void streamingExpandOriginalBufferAfterTypeConversions() throws Exception {
        StreamingHttpClient client = streamingHttpClient();
        Buffer buffer = client.executionContext().bufferAllocator().newBuffer(0, false);
        StreamingHttpRequest request = client.post(SVC_ECHO).payloadBody(from(buffer))
                .toRequest().toFuture().get().toStreamingRequest();
        buffer.writeAscii(CONTENT);
        assertResponse(makeRequest(request), request.version(), OK, CONTENT);
    }
}
