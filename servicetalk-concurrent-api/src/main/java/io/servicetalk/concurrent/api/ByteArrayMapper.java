/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.api.FromInputStreamPublisher.ToByteArrayMapper;

import static io.servicetalk.concurrent.api.FromInputStreamPublisher.DEFAULT_MAX_BUFFER_SIZE;
import static io.servicetalk.concurrent.api.FromInputStreamPublisher.ToByteArrayMapper.DEFAULT_TO_BYTE_ARRAY_MAPPER;

/**
 * A mapper to transform {@code byte[]} buffer regions into a desired type {@code T}.
 *
 * @param <T> Type of the result of this mapper
 */
@FunctionalInterface
public interface ByteArrayMapper<T> {

    /**
     * Maps a specified {@code byte[]} buffer region into a {@code T}.
     * <p>
     * The mapper can operate only with the specified region of the {@code buffer}, which can be safely used without a
     * need to copy data. Access to other parts of the buffer may lead to unexpected results and due care should be
     * taken to avoid leaking that data through the returned type {@code T}.
     *
     * @param buffer {@code byte[]} buffer with data
     * @param offset the offset of the region
     * @param length the length of the region
     * @return result of type {@code T}
     */
    T map(byte[] buffer, int offset, int length);

    /**
     * Returns the maximum allowed buffer size for the {@link #map(byte[], int, int)} operation.
     * <p>
     * Must be a positive number.
     *
     * @return the maximum allowed buffer size for the {@link #map(byte[], int, int)} operation
     */
    default int maxBufferSize() {
        return DEFAULT_MAX_BUFFER_SIZE;
    }

    /**
     * Mapper from the buffer region to an independent {@code byte[]} buffer.
     * <p>
     * Returns {@link #toByteArray(int)} with default {@link #maxBufferSize()}.
     *
     * @return a mapper from the buffer region to an independent {@code byte[]} buffer
     */
    static ByteArrayMapper<byte[]> toByteArray() {
        return DEFAULT_TO_BYTE_ARRAY_MAPPER;
    }

    /**
     * Mapper from the buffer region to an independent {@code byte[]} buffer.
     * <p>
     * Returns the original {@code byte[]} buffer as-is if it was completely full of data or allocates a new buffer for
     * the specified length and copies data. Returned {@code byte[]} buffer is always completely full.
     *
     * @param maxBufferSize the value for {@link #maxBufferSize()}
     * @return a mapper from the buffer region to an independent {@code byte[]} buffer
     */
    static ByteArrayMapper<byte[]> toByteArray(final int maxBufferSize) {
        return new ToByteArrayMapper(maxBufferSize);
    }
}
