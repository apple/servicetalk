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
     * Create a new instance that warns (without rejecting) when a deserialized message exceeds the default threshold
     * ({@value MessageSizeLimiter#DEFAULT_MAX_MESSAGE_SIZE_VALUE} bytes). The default can be changed globally via the
     * {@value MessageSizeLimiter#DEFAULT_MAX_MESSAGE_SIZE_PROPERTY} system property (a temporary property that will be
     * removed in a future release), using the same sign convention as
     * {@link #FixedLengthStreamingSerializer(SerializerDeserializer, ToIntFunction, int)}. Use that constructor to
     * enforce a limit, warn at a different threshold, or disable it per serializer.
     * @param serializer The {@link SerializerDeserializer} used to serialize/deserialize individual objects.
     * @param bytesEstimator Provides the length in bytes for each {@link T} being serialized.
     */
    public FixedLengthStreamingSerializer(final SerializerDeserializer<T> serializer,
                                          final ToIntFunction<T> bytesEstimator) {
        this(serializer, bytesEstimator,
                MessageSizeLimiter.forMaxMessageSize(MessageSizeLimiter.DEFAULT_MAX_MESSAGE_SIZE));
    }

    /**
     * Create a new instance.
     * @param serializer The {@link SerializerDeserializer} used to serialize/deserialize individual objects.
     * @param bytesEstimator Provides the length in bytes for each {@link T} being serialized.
     * @param maxMessageSize The maximum length (in bytes) declared by a frame's length prefix accepted during
     * deserialization. The sign selects the mode, the magnitude the threshold: <em>positive</em> enforces, rejecting a
     * frame declaring a larger length with a {@link io.servicetalk.serializer.api.MaxMessageSizeExceededException}
     * before any of its bytes are buffered; <em>negative</em> warns at {@code abs(value)} bytes (oversized frames are
     * still delivered, with a rate-limited warning); {@code 0} disables it.
     */
    public FixedLengthStreamingSerializer(final SerializerDeserializer<T> serializer,
                                          final ToIntFunction<T> bytesEstimator,
                                          final int maxMessageSize) {
        this(serializer, bytesEstimator, MessageSizeLimiter.forMaxMessageSize(maxMessageSize));
    }

    private FixedLengthStreamingSerializer(final SerializerDeserializer<T> serializer,
                                           final ToIntFunction<T> bytesEstimator,
                                           final MessageSizeLimiter sizeLimiter) {
        this.serializer = requireNonNull(serializer);
        this.bytesEstimator = requireNonNull(bytesEstimator);
        this.sizeLimiter = sizeLimiter;
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
