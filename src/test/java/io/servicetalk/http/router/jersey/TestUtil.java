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

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpResponse;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class TestUtil {
    private TestUtil() {
        // no instances
    }

    public static String getContentAsString(final HttpResponse<HttpPayloadChunk> res) {
        return getContentAsString(res.getMessageBody());
    }

    public static String getContentAsString(final Publisher<HttpPayloadChunk> content) {
        try {
            //noinspection ConstantConditions
            return awaitIndefinitely(content
                    .reduce(StringBuilder::new, (sb, c) -> sb.append(c.getContent().toString(UTF_8)))
                    .map(StringBuilder::toString));
        } catch (final Throwable t) {
            throw new IllegalStateException("Failed to extract content from: " + content, t);
        }
    }

    public static Publisher<HttpPayloadChunk> asChunkPublisher(final String content, final BufferAllocator allocator) {
        return just(newPayloadChunk(allocator.fromUtf8(content)), immediate());
    }

    public static Publisher<HttpPayloadChunk> asChunkPublisher(final byte[] content, final BufferAllocator allocator) {
        return just(newPayloadChunk(allocator.wrap(content)), immediate());
    }
}
