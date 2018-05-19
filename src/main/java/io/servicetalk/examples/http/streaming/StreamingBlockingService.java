/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.streaming;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.http.api.BlockingHttpRequest;
import io.servicetalk.http.api.BlockingHttpResponse;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.transport.api.ConnectionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.http.api.BlockingHttpResponses.newResponse;
import static io.servicetalk.http.api.HttpHeaderNames.SERVER;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;

/**
 * Business logic for the Hello World application.
 * This is a blocking service, for asynchronous use {@link StreamingService}.
 */
final class StreamingBlockingService extends BlockingHttpService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingBlockingService.class);

    @Override
    public BlockingHttpResponse<HttpPayloadChunk> handle(final ConnectionContext ctx,
                                                         final BlockingHttpRequest<HttpPayloadChunk> request) {
        // Log the request meta data and headers, by default the header values will be filtered for
        // security reasons, however here we override the filter and print every value.
        LOGGER.info("got request {}", request.toString((name, value) -> value));

        final BufferAllocator allocator = ctx.getExecutionContext().getBufferAllocator();

        // Iterate through all the request payload chunks. Note that data is streaming in the background so this
        // may be synchronous as opposed to blocking, but if data is not available the APIs will have to block.
        List<HttpPayloadChunk> responsePayload = new ArrayList<>();
        for (HttpPayloadChunk requestChunk : request.getPayloadBody()) {
            if (requestChunk.getContent().getReadableBytes() != 0) {
                responsePayload.add(newPayloadChunk(allocator.newCompositeBuffer()
                                 .addBuffer(allocator.fromAscii("hello, "))
                                 .addBuffer(requestChunk.getContent())
                                 .addBuffer(allocator.fromAscii("!"))));
            }
        }

        BlockingHttpResponse<HttpPayloadChunk> response = newResponse(OK, responsePayload);
        response.getHeaders().set(TRANSFER_ENCODING, CHUNKED).set(SERVER, "ServiceTalkHelloWorldBlockingServer");

        return response;
    }
}
