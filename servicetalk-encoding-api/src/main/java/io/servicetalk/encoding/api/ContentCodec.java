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
 * <em>Implementations must provide thread safety semantics, since instances could be shared across threads.</em>
 * @deprecated Use {@link BufferEncoder} and {@link BufferDecoder}.
 */
@Deprecated
public interface ContentCodec {

    /**
     * A unique textual representation for the coding.
     *
     * @return a unique textual representation for the coding.
     */
    CharSequence name();

    /**
     * Take a {@link Buffer} and encode its contents resulting in a {@link Buffer} with the encoded contents.
     * This call increases the {@link Buffer#readerIndex()} of the {@code src} with the number
     * of bytes available to read {@link Buffer#readableBytes()}.
     *
     * @param src the {@link Buffer} to encode
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link Buffer} the result buffer with the content encoded
     */
    Buffer encode(Buffer src, BufferAllocator allocator);

    /**
     * Take a {@link Buffer} and decode its contents resulting in a {@link Buffer} with the decoded content.
     * This call increases the {{@link Buffer#readerIndex()} of the {@code src} with the number of
     * bytes available to read {@link Buffer#readableBytes()}.
     *
     * @param src the {@link Buffer} to decode
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link Buffer} the result buffer with the content decoded
     */
    Buffer decode(Buffer src, BufferAllocator allocator);

    /**
     * Take a {@link Publisher} of {@link Buffer} and encode its contents resulting in a
     * {@link Publisher} of {@link Buffer} with the encoded contents.
     *
     * @param from the {@link Publisher} buffer to encode
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link Publisher} the result publisher with the buffers encoded
     */
    Publisher<Buffer> encode(Publisher<Buffer> from, BufferAllocator allocator);

    /**
     * Take a {@link Publisher} of {@link Buffer} and encode its contents resulting in a
     * {@link Publisher} of {@link Buffer} with the decoded contents.
     *
     * @param from the {@link Publisher} to decoded
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link Publisher} the result publisher with the buffers decoded
     */
    Publisher<Buffer> decode(Publisher<Buffer> from, BufferAllocator allocator);
}
