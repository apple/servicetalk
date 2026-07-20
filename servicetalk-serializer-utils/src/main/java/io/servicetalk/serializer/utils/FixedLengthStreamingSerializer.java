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
package io.servicetalk.serializer.utils;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import java.util.function.BiFunction;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

import static java.lang.Integer.BYTES;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * A {@link StreamingSerializerDeserializer} that uses a {@link SerializerDeserializer} and frames each object by
 * preceding it with the length in bytes. The length component is fixed and always consumes 4 bytes.
 * @param <T> The type of object to serialize.
 */
public final class FixedLengthStreamingSerializer<T> implements StreamingSerializerDeserializer<T> {
    private final SerializerDeserializer<T> serializer;
    private final ToIntFunction<T> bytesEstimator;
    private final MessageSizeLimiter sizeLimiter;

    /**
     * Create a new instance that limits deserialized messages to the default maximum size
     * ({@value MessageSizeLimiter#DEFAULT_MAX_MESSAGE_SIZE_VALUE} bytes, overridable via the
     * {@value MessageSizeLimiter#DEFAULT_MAX_MESSAGE_SIZE_PROPERTY} system property). Use
     * {@link #FixedLengthStreamingSerializer(SerializerDeserializer, ToIntFunction, int)} to configure a different
     * limit, disable it, or warn instead of rejecting.
     * @param serializer The {@link SerializerDeserializer} used to serialize/deserialize individual objects.
     * @param bytesEstimator Provides the length in bytes for each {@link T} being serialized.
     */
    public FixedLengthStreamingSerializer(final SerializerDeserializer<T> serializer,
                                          final ToIntFunction<T> bytesEstimator) {
        this(serializer, bytesEstimator, MessageSizeLimiter.DEFAULT_MAX_MESSAGE_SIZE);
    }

    /**
     * Create a new instance.
     * @param serializer The {@link SerializerDeserializer} used to serialize/deserialize individual objects.
     * @param bytesEstimator Provides the length in bytes for each {@link T} being serialized.
     * @param maxMessageSize The maximum length (in bytes) declared by a frame's length prefix that will be accepted
     * during deserialization. A frame declaring a larger length is rejected with a
     * {@link io.servicetalk.serializer.api.MaxMessageSizeExceededException} before any of its bytes are buffered.
     * {@code 0} disables the limit; {@code -1} logs a rate-limited warning at the default threshold but lets the
     * message through (a rollout aid, not steady-state protection); other negative values are rejected.
     */
    public FixedLengthStreamingSerializer(final SerializerDeserializer<T> serializer,
                                          final ToIntFunction<T> bytesEstimator,
                                          final int maxMessageSize) {
        this.serializer = requireNonNull(serializer);
        this.bytesEstimator = requireNonNull(bytesEstimator);
        this.sizeLimiter = MessageSizeLimiter.forMaxMessageSize(maxMessageSize);
    }

    @Override
    public Publisher<T> deserialize(final Publisher<Buffer> serializedData, final BufferAllocator allocator) {
        return serializedData.liftSync(new FramedDeserializerOperator<>(serializer,
                        () -> new LengthDeframer(sizeLimiter), allocator))
                .flatMapConcatIterable(identity());
    }

    @Override
    public Publisher<Buffer> serialize(final Publisher<T> toSerialize, final BufferAllocator allocator) {
        return toSerialize.map(t -> {
            Buffer buffer = allocator.newBuffer(BYTES + bytesEstimator.applyAsInt(t));
            final int beforeWriterIndex = buffer.writerIndex();
            buffer.writerIndex(beforeWriterIndex + BYTES);
            serializer.serialize(t, allocator, buffer);
            buffer.setInt(beforeWriterIndex, buffer.writerIndex() - beforeWriterIndex - BYTES);
            return buffer;
        });
    }

    private static final class LengthDeframer implements BiFunction<Buffer, BufferAllocator, Buffer> {
        private final MessageSizeLimiter sizeLimiter;
        private int expectedLength = -1;

        LengthDeframer(final MessageSizeLimiter sizeLimiter) {
            this.sizeLimiter = sizeLimiter;
        }

        @Nullable
        @Override
        public Buffer apply(final Buffer buffer, final BufferAllocator allocator) {
            if (expectedLength < 0) {
                if (buffer.readableBytes() < BYTES) {
                    return null;
                }
                expectedLength = buffer.readInt();
                if (expectedLength < 0) {
                    throw new SerializationException("Invalid length: " + expectedLength);
                }
                sizeLimiter.checkMessageSize(expectedLength);
            }
            if (buffer.readableBytes() < expectedLength) {
                return null;
            }
            Buffer result = buffer.readBytes(expectedLength);
            expectedLength = -1;
            return result;
        }
    }
}
