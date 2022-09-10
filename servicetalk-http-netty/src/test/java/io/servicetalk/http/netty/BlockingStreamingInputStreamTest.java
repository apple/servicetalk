/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class BlockingStreamingInputStreamTest {

    @Test
    void testInputStreamCanBeAccessedMultipleTimes() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingStreamingAndAwait((ctx, request, response) -> {
                    try (HttpPayloadWriter<Buffer> writer = response.sendMetaData()) {
                        int b;
                        while ((b = request.payloadBodyInputStream().read()) >= 0) {
                            Buffer buffer = ctx.executionContext().bufferAllocator().newBuffer(1);
                            buffer.writeByte(b);
                            writer.write(buffer);
                        }
                    }
                });
             BlockingStreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .buildBlockingStreaming()) {
            String content = "content";
            InputStream payloadIs = new ByteArrayInputStream(content.getBytes(US_ASCII));
            BlockingStreamingHttpResponse response = client.request(client.post("/")
                    .payloadBody(payloadIs));
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = response.payloadBodyInputStream().read()) >= 0) {
                sb.append((char) b);
            }
            assertThat(sb.toString(), is(equalTo(content)));
        }
    }
}
