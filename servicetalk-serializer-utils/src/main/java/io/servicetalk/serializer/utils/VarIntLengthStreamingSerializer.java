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

import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

/**
 * A {@link StreamingSerializerDeserializer} that uses a {@link SerializerDeserializer} and frames each object by
 * preceding it with the length in bytes. The length component is variable length and encoded as
 * <a href="https://developers.google.com/protocol-buffers/docs/encoding">base 128 VarInt</a>.
 * @param <T> The type of object to serialize.
 */
public final class VarIntLengthStreamingSerializer<T> implements StreamingSerializerDeserializer<T> {
    // 0xxx xxxx
    static final int ONE_BYTE_VAL = 1 << 7;
    // 1xxx xxxx 0xxx xxxx
    static final int TWO_BYTE_VAL = 1 << 14;
    // 1xxx xxxx 0xxx xxxx 0xxx xxxx
    static final int THREE_BYTE_VAL = 1 << 21;
    // 1xxx xxxx 0xxx xxxx 0xxx xxxx 0xxx xxxx
    static final int FOUR_BYTE_VAL = 1 << 28;
    static final int MAX_LENGTH_BYTES = 5;
    private final SerializerDeserializer<T> serializer;
    private final ToIntFunction<T> bytesEstimator;

    /**
     * Create a new instance.
     *
     * @param serializer The {@link SerializerDeserializer} used to serialize/deserialize individual objects.
     * @param bytesEstimator Estimates the length in bytes for each {@link T} being serialized.
     */
    public VarIntLengthStreamingSerializer(final SerializerDeserializer<T> serializer,
                                           final ToIntFunction<T> bytesEstimator) {
        this.serializer = requireNonNull(serializer);
        this.bytesEstimator = requireNonNull(bytesEstimator);
    }

    @Override
    public Publisher<T> deserialize(final Publisher<Buffer> serializedData, final BufferAllocator allocator) {
        return serializedData.liftSync(new FramedDeserializerOperator<>(serializer, LengthDeframer::new, allocator))
                .flatMapConcatIterable(identity());
    }

    @Override
    public Publisher<Buffer> serialize(final Publisher<T> toSerialize, final BufferAllocator allocator) {
        return toSerialize.map(t -> {
            Buffer buffer = allocator.newBuffer(MAX_LENGTH_BYTES + bytesEstimator.applyAsInt(t));
            final int beforeWriterIndex = buffer.writerIndex();
            buffer.writerIndex(beforeWriterIndex + MAX_LENGTH_BYTES);
            serializer.serialize(t, allocator, buffer);
            final int length = buffer.writerIndex() - beforeWriterIndex - MAX_LENGTH_BYTES;
            setVarInt(length, buffer, beforeWriterIndex);
            return buffer;
        });
    }

    private static final class LengthDeframer implements BiFunction<Buffer, BufferAllocator, Buffer> {
        private int expectedLength = -1;

        @Nullable
        @Override
        public Buffer apply(final Buffer buffer, final BufferAllocator allocator) {
            if (expectedLength < 0) {
                expectedLength = getVarInt(buffer);
                if (expectedLength < 0) {
                    return null;
                }
            }
            if (buffer.readableBytes() < expectedLength) {
                return null;
            }
            Buffer result = buffer.readBytes(expectedLength);
            expectedLength = -1;
            return result;
        }
    }

    static int getVarInt(Buffer buffer) {
        final int maxBytesToInspect = min(MAX_LENGTH_BYTES, buffer.readableBytes());
        final int readerIndex = buffer.readerIndex();
        int i = 0;
        for (; i < maxBytesToInspect; ++i) {
            final byte b = buffer.getByte(i + readerIndex);
            if ((b & 0x80) == 0) {
                if (i == 0) {
                    return buffer.readByte();
                } else if (i == 1) {
                    final short varInt = buffer.readShort();
                    return ((varInt & 0x7F) << 7) |
                           ((varInt & 0x7F00) >> 8);
                } else if (i == 2) {
                    final int varInt = buffer.readMedium();
                    return ((varInt & 0x7F) << 14) |
                           ((varInt & 0x7F00) >> 1) |
                           ((varInt & 0x7F0000) >> 16);
                } else if (i == 3) {
                    final int varInt = buffer.readInt();
                    return ((varInt & 0x7F) << 21) |
                            ((varInt & 0x7F00) << 6) |
                            ((varInt & 0x7F0000) >> 9) |
                            ((varInt & 0x7F000000) >> 24);
                } else {
                    assert i == 4;
                    final byte b0 = buffer.readByte();
                    final int varInt = buffer.readInt();
                    if ((varInt & 0xF8) != 0) {
                        throw new SerializationException("java int cannot support larger than " + Integer.MAX_VALUE);
                    }
                    return ((varInt & 0x7) << 28) |
                           ((varInt & 0x7F00) << 13) |
                           ((varInt & 0x7F0000) >> 2) |
                           ((varInt & 0x7F000000) >> 17) |
                            (b0 & 0x7F);
                }
            }
        }
        if (i == MAX_LENGTH_BYTES) {
            throw new SerializationException("java int cannot support more than " + MAX_LENGTH_BYTES +
                    " bytes of VarInt");
        }

        return -1;
    }

    static void setVarInt(int val, Buffer buffer, int index) {
        assert val >= 0;
        if (val < ONE_BYTE_VAL) {
            // The initial buffer allocation allows for MAX_LENGTH_BYTES space to encode the length because we don't
            // know the length of the serialization until it is completed, and we want to avoid copying data to write
            // the length prefix. So we write the length adjacent to the data and skip the bytes not required to encode
            // the length.
            buffer.setByte(index + 4, (byte) val).skipBytes(4);
        } else if (val < TWO_BYTE_VAL) {
            final int varIntEncoded =
                    0x8000 | ((val & 0x7F) << 8) |
                    ((val & 0x3F80) >> 7);
            buffer.setShort(index + 3, varIntEncoded).skipBytes(3);
        } else if (val < THREE_BYTE_VAL) {
            final int varIntEncoded =
                    0x800000 | ((val & 0x7F) << 16) |
                    0x8000 | ((val & 0x3F80) << 1) |
                    ((val & 0x1FC000) >> 14);
            buffer.setMedium(index + 2, varIntEncoded).skipBytes(2);
        } else if (val < FOUR_BYTE_VAL) {
            final int varIntEncoded =
                    0x80000000 | ((val & 0x7F) << 24) |
                    0x800000 | ((val & 0x3F80) << 9) |
                    0x8000 | ((val & 0x1FC000) >> 6) |
                    ((val & 0xFE00000) >> 21);
            buffer.setInt(index + 1, varIntEncoded).skipBytes(1);
        } else {
            buffer.setByte(index, 0x80 | (val & 0x7F));
            final int varIntEncoded =
                    0x80000000 | ((val & 0x3F80) << 17) |
                    0x800000 | ((val & 0x1FC000) << 2) |
                    0x8000 | ((val & 0xFE00000) >> 13) |
                    ((val & 0xF0000000) >> 28);
            buffer.setInt(index + 1, varIntEncoded);
        }
    }
}
