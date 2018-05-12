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
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpResponse;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.HttpPayloadChunks.aggregateChunks;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class TestUtils {
    private TestUtils() {
        // no instances
    }

    public static String getContentAsString(final HttpResponse<HttpPayloadChunk> res) {
        return getContentAsString(res.getPayloadBody());
    }

    public static String getContentAsString(final Publisher<HttpPayloadChunk> content) {
        try {
            return awaitIndefinitelyNonNull(
                    aggregateChunks(content, DEFAULT_ALLOCATOR).map(c -> c.getContent().toString(UTF_8)));
        } catch (final Throwable t) {
            throw new IllegalStateException("Failed to extract content from: " + content, t);
        }
    }

    public static Publisher<HttpPayloadChunk> asChunkPublisher(final String content,
                                                               final BufferAllocator allocator) {
        return asChunkPublisher(allocator.fromUtf8(content));
    }

    public static Publisher<HttpPayloadChunk> asChunkPublisher(final byte[] content,
                                                               final BufferAllocator allocator) {
        return asChunkPublisher(allocator.wrap(content));
    }

    private static Publisher<HttpPayloadChunk> asChunkPublisher(final Buffer content) {
        return just(newPayloadChunk(content));
    }
}
