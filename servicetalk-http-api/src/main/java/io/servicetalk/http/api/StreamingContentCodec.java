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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;

/**
 * Used to encode and decode {@link Publisher} of {@link Buffer} parts.
 * This instance is shared therefore it must provide thread safety semantics.
 */
public interface StreamingContentCodec extends ContentCodec {

    /**
     * Take a {@link Publisher} of {@link Buffer} and encode its contents resulting in a
     * {@link Publisher} of {@link Buffer} with the encoded contents.
     *
     * @param from the {@link Publisher} buffer to encode
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link Publisher} the result publisher with the buffers encoded
     */
    default Publisher<Buffer> encode(Publisher<Buffer> from, BufferAllocator allocator) {
        return from.map((buffer -> encode(buffer, allocator)));
    }

    /**
     * Take a {@link Publisher} of {@link Buffer} and encode its contents resulting in a
     * {@link Publisher} of {@link Buffer} with the decoded contents.
     *
     * @param from the {@link Publisher} to decoded
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link Publisher} the result publisher with the buffers decoded
     */
    default Publisher<Buffer> decode(Publisher<Buffer> from, BufferAllocator allocator) {
        return from.map(buffer -> decode(buffer, allocator));
    }
}
