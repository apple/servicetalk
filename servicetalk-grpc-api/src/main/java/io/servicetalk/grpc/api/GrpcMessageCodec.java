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
package io.servicetalk.grpc.api;

import io.servicetalk.buffer.api.BufferAllocator;

import java.nio.ByteBuffer;

/**
 * Codec used to encode and decode gRPC messages.
 * This instance is shared across all requests/responses therefore it must provide thread safety semantics.
 */
public interface GrpcMessageCodec {

    /**
     * Take a {@link ByteBuffer} and encode its contents resulting in a {@link ByteBuffer} with the encoded contents.
     *
     * @param src the {@link ByteBuffer} to encode
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link ByteBuffer} the result buffer with the content encoded
     */
    ByteBuffer encode(ByteBuffer src, BufferAllocator allocator);

    /**
     * Take a {@link ByteBuffer} and decode its contents resulting in a {@link ByteBuffer} with the decoded content.
     *
     * @param src the {@link ByteBuffer} to decode
     * @param allocator the {@link BufferAllocator} to use for allocating auxiliary buffers or the returned buffer
     * @return {@link ByteBuffer} the result buffer with the content decoded
     */
    ByteBuffer decode(ByteBuffer src, BufferAllocator allocator);
}
