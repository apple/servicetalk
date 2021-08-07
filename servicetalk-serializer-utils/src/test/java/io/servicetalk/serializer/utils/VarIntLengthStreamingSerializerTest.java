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
import io.servicetalk.serializer.api.SerializationException;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.serializer.utils.StringSerializer.stringSerializer;
import static io.servicetalk.serializer.utils.VarIntLengthStreamingSerializer.FOUR_BYTE_VAL;
import static io.servicetalk.serializer.utils.VarIntLengthStreamingSerializer.MAX_LENGTH_BYTES;
import static io.servicetalk.serializer.utils.VarIntLengthStreamingSerializer.ONE_BYTE_VAL;
import static io.servicetalk.serializer.utils.VarIntLengthStreamingSerializer.THREE_BYTE_VAL;
import static io.servicetalk.serializer.utils.VarIntLengthStreamingSerializer.TWO_BYTE_VAL;
import static io.servicetalk.serializer.utils.VarIntLengthStreamingSerializer.getVarInt;
import static io.servicetalk.serializer.utils.VarIntLengthStreamingSerializer.setVarInt;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VarIntLengthStreamingSerializerTest {
    @Test
    void decodeThrowsIfMoreThanMaxBytes() {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(MAX_LENGTH_BYTES);
        byte nonFinalByte = (byte) 0x80;
        for (int i = 0; i <= MAX_LENGTH_BYTES; ++i) {
            buffer.writeByte(nonFinalByte);
        }

        assertThrows(SerializationException.class, () -> getVarInt(buffer));
    }

    @Test
    void decodeThrowsIfGreaterThanMaxInt() {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(MAX_LENGTH_BYTES);
        byte nonFinalByte = (byte) 0xFF;
        for (int i = 0; i < MAX_LENGTH_BYTES - 1; ++i) {
            buffer.writeByte(nonFinalByte);
        }
        buffer.writeByte((byte) 0xF);

        assertThrows(SerializationException.class, () -> getVarInt(buffer));
    }

    @ParameterizedTest(name = "val={0}")
    @MethodSource("boundaries")
    void encodeAndDecodeBoundaries(int val) {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(MAX_LENGTH_BYTES * 2)
                .writerIndex(MAX_LENGTH_BYTES); // setVarInt expects to write before writer index, give it space.
        setVarInt(val, buffer, buffer.readerIndex());
        assertThat(getVarInt(buffer), is(val));
        assertThat(buffer.readableBytes(), is(0));
    }

    @Test
    void serializeDeserialize() throws Exception {
        VarIntLengthStreamingSerializer<String> serializer = new VarIntLengthStreamingSerializer<>(
                stringSerializer(UTF_8), String::length);

        assertThat(serializer.deserialize(serializer.serialize(from("foo", "bar"), DEFAULT_ALLOCATOR),
                DEFAULT_ALLOCATOR).toFuture().get(), contains("foo", "bar"));
    }

    @ParameterizedTest(name = "val={0}")
    @MethodSource("boundaries")
    void protobufVarIntDecodeCompatibility(int val) throws IOException {
        byte[] array = new byte[MAX_LENGTH_BYTES];
        CodedOutputStream oStream = CodedOutputStream.newInstance(array);
        oStream.writeInt32NoTag(val);
        Buffer buffer = DEFAULT_ALLOCATOR.wrap(array).writerIndex(array.length - oStream.spaceLeft());
        assertThat(getVarInt(buffer), is(val));
    }

    @ParameterizedTest(name = "val={0}")
    @MethodSource("boundaries")
    void protobufVarIntEncodeCompatibility(int val) throws IOException {
        // setVarInt expects to write before writer index, give it space.
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(MAX_LENGTH_BYTES).writerIndex(MAX_LENGTH_BYTES);
        setVarInt(val, buffer, buffer.readerIndex());

        // Copy bytes into the destination array, so protobuf sees values starting at 0 index.
        // setVarInt will encode the value packed to the right of the array, because the value is encoded into
        // the array first and we want to avoid copying/moving serialized bytes just to write the size.
        byte[] array = new byte[buffer.readableBytes()];
        buffer.readBytes(array);

        assertThat(CodedInputStream.newInstance(array).readInt32(), is(val));
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> boundaries() {
        return Stream.of(
                Arguments.of(0),
                Arguments.of(1),
                Arguments.of(ONE_BYTE_VAL - 1),
                Arguments.of(ONE_BYTE_VAL),
                Arguments.of(ONE_BYTE_VAL + 1),
                Arguments.of(TWO_BYTE_VAL - 1),
                Arguments.of(TWO_BYTE_VAL),
                Arguments.of(TWO_BYTE_VAL + 1),
                Arguments.of(THREE_BYTE_VAL - 1),
                Arguments.of(THREE_BYTE_VAL),
                Arguments.of(THREE_BYTE_VAL + 1),
                Arguments.of(FOUR_BYTE_VAL - 1),
                Arguments.of(FOUR_BYTE_VAL),
                Arguments.of(FOUR_BYTE_VAL + 1),
                Arguments.of(Integer.MAX_VALUE - 1),
                Arguments.of(Integer.MAX_VALUE)
        );
    }
}
