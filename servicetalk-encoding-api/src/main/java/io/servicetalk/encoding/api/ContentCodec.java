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
package io.servicetalk.encoding.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;

/**
 * API to support encode and decode of {@link Buffer}s.
 * <p>
 * <em>This instance is shared therefore it must provide thread safety semantics.</em>
 */
public interface ContentCodec {

    /**
     * A unique textual representation for the coding.
     *
     * @return a unique textual representation for the coding.
     */
    CharSequence name();

    /**
     * Take a {@link Buffer} and encode its contents resulting in a {@link Buffer} with the encoded contents.
     *
     * @param src the {@link Buffer} to encode
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link Buffer} the result buffer with the content encoded
     */
    default Buffer encode(Buffer src, BufferAllocator allocator) {
        return encode(src, src.readerIndex(), src.readableBytes(), allocator);
    }

    /**
     * Take a {@link Buffer} and encode its contents resulting in a {@link Buffer} with the encoded contents.
     *
     * @param src the {@link Buffer} to encode
     * @param offset the offset of the source to start reading from
     * @param length the total length available for reading
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link Buffer} the result buffer with the content encoded
     */
    Buffer encode(Buffer src, int offset, int length, BufferAllocator allocator);

    /**
     * Take a {@link Buffer} and decode its contents resulting in a {@link Buffer} with the decoded content.
     *
     * @param src the {@link Buffer} to decode
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link Buffer} the result buffer with the content decoded
     */
    default Buffer decode(Buffer src, BufferAllocator allocator) {
        return decode(src, src.readerIndex(), src.readableBytes(), allocator);
    }

    /**
     * Take a {@link Buffer} and decode its contents resulting in a {@link Buffer} with the decoded content.
     *
     * @param src the {@link Buffer} to decode
     * @param offset the offset of the source to start reading from
     * @param length the total length available for reading
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link Buffer} the result buffer with the content decoded
     */
    Buffer decode(Buffer src, int offset, int length, BufferAllocator allocator);

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
