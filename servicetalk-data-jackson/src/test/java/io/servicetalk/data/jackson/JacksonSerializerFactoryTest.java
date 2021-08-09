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
package io.servicetalk.data.jackson;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_DIRECT_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.PREFER_HEAP_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.data.jackson.JacksonSerializerFactory.JACKSON;
import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JacksonSerializerFactoryTest {
    private static final TypeReference<TestPojo> TEST_POJO_TYPE_REFERENCE = new TypeReference<TestPojo>() { };
    private static final TypeReference<String> STRING_TYPE_REFERENCE = new TypeReference<String>() { };
    private static final TypeReference<Boolean> BOOLEAN_TYPE_REFERENCE = new TypeReference<Boolean>() { };
    private static final TypeReference<Integer> INTEGER_TYPE_REFERENCE = new TypeReference<Integer>() { };

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void aggregatedSerializeDeserialize(boolean typeRef, BufferAllocator alloc) {
        TestPojo expected = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null, new String[] {"bar"},
                null);

        assertEquals(expected, pojoSerializer(typeRef).deserialize(
                pojoSerializer(typeRef).serialize(expected, alloc), alloc));
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void deserializeInvalidData(boolean typeRef, BufferAllocator alloc) {
        TestPojo expected = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null, new String[] {"bar"},
                null);
        final Buffer serialized = pojoSerializer(typeRef).serialize(expected, alloc);
        serialized.setByte(serialized.writerIndex() - 1, serialized.getByte(serialized.writerIndex() - 1) + 1);

        assertThrows(io.servicetalk.serializer.api.SerializationException.class, () ->
                pojoSerializer(typeRef).deserialize(serialized, alloc));
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void deserializeEmpty(boolean typeRef, BufferAllocator alloc) {
        assertThrows(io.servicetalk.serializer.api.SerializationException.class,
                () -> pojoSerializer(typeRef).deserialize(EMPTY_BUFFER, alloc));
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void deserializeIncomplete(boolean typeRef, BufferAllocator alloc) {
        TestPojo expected = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null, new String[] {"bar"},
                null);
        Buffer buffer = pojoSerializer(typeRef).serialize(expected, alloc);
        assertThrows(io.servicetalk.serializer.api.SerializationException.class, () ->
                pojoSerializer(typeRef).deserialize(buffer.readBytes(buffer.readableBytes() - 1), alloc));
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void deserializeEmptyStreaming(boolean typeRef, BufferAllocator alloc) {
        assertThat(pojoStreamingSerializer(typeRef).deserialize(singletonList(EMPTY_BUFFER), alloc),
                emptyIterable());
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void streamingSerializeDeserialize(boolean typeRef, BufferAllocator alloc) {
        TestPojo expected = new TestPojo(true, Byte.MAX_VALUE, Short.MAX_VALUE, Character.MAX_VALUE, Integer.MIN_VALUE,
                Long.MAX_VALUE, Float.MAX_VALUE, Double.MAX_VALUE, "foo", new String[] {"bar", "baz"}, null);

        assertThat(pojoStreamingSerializer(typeRef).deserialize(
                    pojoStreamingSerializer(typeRef).serialize(singletonList(expected), alloc),
                alloc), contains(expected));
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void streamingSerializeDeserialize2(boolean typeRef, BufferAllocator alloc) {
        TestPojo expected1 = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null,
                new String[] {"bar", "baz"}, null);
        TestPojo expected2 = new TestPojo(false, (byte) 500, (short) 353, 'r', 100, 534, 33.25f, 888.5, null,
                new String[] {"foo"}, expected1);
        assertThat(pojoStreamingSerializer(typeRef).deserialize(
                pojoStreamingSerializer(typeRef).serialize(from(expected1, expected2), alloc), alloc)
                .toIterable(), contains(expected1, expected2));
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void streamingSerializeDeserialize2SingleBuffer(boolean typeRef, BufferAllocator alloc) {
        TestPojo expected1 = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null,
                new String[] {"bar", "baz"}, null);
        TestPojo expected2 = new TestPojo(false, (byte) 500, (short) 353, 'r', 100, 534, 33.25f, 888.5, null,
                new String[] {"foo"}, expected1);

        final Buffer buffer1 = pojoSerializer(typeRef).serialize(expected1, alloc);
        final Buffer buffer2 = pojoSerializer(typeRef).serialize(expected2, alloc);

        Buffer composite = alloc.newBuffer(buffer1.readableBytes() + buffer2.readableBytes());
        composite.writeBytes(buffer1).writeBytes(buffer2);

        assertThat(pojoStreamingSerializer(typeRef).deserialize(from(composite), alloc)
                .toIterable(), contains(expected1, expected2));
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void streamingSerializeDeserializeMultipleSplitBuffers(boolean typeRef, BufferAllocator alloc) {
        TestPojo expected1 = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null,
                new String[] {"bar", "baz"}, null);
        TestPojo expected2 = new TestPojo(false, (byte) 500, (short) 353, 'r', 100, 534, 33.25f, 888.5, null,
                new String[] {"foo"}, expected1);

        final Buffer buffer1 = pojoSerializer(typeRef).serialize(expected1, alloc);
        final Buffer buffer2 = pojoSerializer(typeRef).serialize(expected2, alloc);

        int buffer1Split = ThreadLocalRandom.current().nextInt(buffer1.readableBytes());
        int buffer2Split = ThreadLocalRandom.current().nextInt(buffer2.readableBytes());
        String debugString = splitDebugString(buffer1, buffer1Split) + lineSeparator() +
                splitDebugString(buffer2, buffer2Split);
        try {
            assertThat(pojoStreamingSerializer(typeRef).deserialize(
                    from(buffer1.readBytes(buffer1Split), buffer1, buffer2.readBytes(buffer2Split), buffer2),
                    alloc).toIterable(), contains(expected1, expected2));
        } catch (Throwable cause) {
            throw new AssertionError("failed to parse split buffers: " + debugString, cause);
        }
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void deserializeString(boolean typeRef, BufferAllocator alloc) {
        String json = "\"x\"";
        final Buffer buffer = alloc.fromAscii(json);
        assertThat(stringSerializer(typeRef).deserialize(buffer, alloc), is("x"));
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void streamingDeserializeString(boolean typeRef, BufferAllocator alloc) {
        String json = "\"x\"";
        final Buffer buffer = alloc.fromAscii(json);
        assertThat(stringStreamingSerializer(typeRef).deserialize(from(buffer), alloc).toIterable(),
                    contains("x"));
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void deserializeStringNull(boolean typeRef, BufferAllocator alloc) {
        String json = "null";
        final Buffer buffer = alloc.fromAscii(json);
        assertThat(stringSerializer(typeRef).deserialize(buffer, alloc), nullValue());
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void streamingDeserializeStringNull(boolean typeRef, BufferAllocator alloc) {
        String json = "null";
        final Buffer buffer = alloc.fromAscii(json);
        assertThat(stringStreamingSerializer(typeRef).deserialize(from(buffer), alloc).toIterable(),
                emptyIterable());
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void deserializeBoolean(boolean typeRef, BufferAllocator alloc) {
        String json = "true";
        final Buffer buffer = alloc.fromAscii(json);
        assertThat(boolSerializer(typeRef).deserialize(buffer, alloc), is(true));
    }

    @Disabled("Jackson streaming currently does not support parsing only Boolean value.")
    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void streamingDeserializeBoolean(boolean typeRef, BufferAllocator alloc) {
        String json = "true";
        final Buffer buffer = alloc.fromAscii(json);
        assertThat(boolStreamingSerializer(typeRef).deserialize(from(buffer), alloc).toIterable(),
                contains(true));
    }

    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void deserializeInteger(boolean typeRef, BufferAllocator alloc) {
        String json = "1";
        final Buffer buffer = alloc.fromAscii(json);
        assertThat(intSerializer(typeRef).deserialize(buffer, alloc), is(1));
    }

    @Disabled("Jackson streaming currently does not support parsing only Integer value.")
    @ParameterizedTest(name = "{index}, typeRef={0}, alloc={1}")
    @MethodSource("params")
    void streamingDeserializeInteger(boolean typeRef, BufferAllocator alloc) {
        String json = "1";
        final Buffer buffer = alloc.fromAscii(json);
        assertThat(intStreamingSerializer(typeRef).deserialize(from(buffer), alloc).toIterable(), contains(1));
    }

    private static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of(true, PREFER_DIRECT_ALLOCATOR),
                Arguments.of(true, PREFER_HEAP_ALLOCATOR),
                Arguments.of(false, PREFER_DIRECT_ALLOCATOR),
                Arguments.of(false, PREFER_HEAP_ALLOCATOR));
    }

    private static String splitDebugString(Buffer buffer, int split) {
        return buffer.toString(buffer.readerIndex(), split, UTF_8) + lineSeparator() +
                buffer.toString(buffer.readerIndex() + split, buffer.readableBytes() - split, UTF_8);
    }

    private static SerializerDeserializer<TestPojo> pojoSerializer(boolean typeRef) {
        return typeRef ? JACKSON.serializerDeserializer(TEST_POJO_TYPE_REFERENCE) :
                JACKSON.serializerDeserializer(TestPojo.class);
    }

    private static StreamingSerializerDeserializer<TestPojo> pojoStreamingSerializer(boolean typeRef) {
        return typeRef ? JACKSON.streamingSerializerDeserializer(TEST_POJO_TYPE_REFERENCE) :
                JACKSON.streamingSerializerDeserializer(TestPojo.class);
    }

    private static SerializerDeserializer<String> stringSerializer(boolean typeRef) {
        return typeRef ? JACKSON.serializerDeserializer(STRING_TYPE_REFERENCE) :
                JACKSON.serializerDeserializer(String.class);
    }

    private static StreamingSerializerDeserializer<String> stringStreamingSerializer(boolean typeRef) {
        return typeRef ? JACKSON.streamingSerializerDeserializer(STRING_TYPE_REFERENCE) :
                JACKSON.streamingSerializerDeserializer(String.class);
    }

    private static SerializerDeserializer<Boolean> boolSerializer(boolean typeRef) {
        return typeRef ? JACKSON.serializerDeserializer(BOOLEAN_TYPE_REFERENCE) :
                JACKSON.serializerDeserializer(Boolean.class);
    }

    private static StreamingSerializerDeserializer<Boolean> boolStreamingSerializer(boolean typeRef) {
        return typeRef ? JACKSON.streamingSerializerDeserializer(BOOLEAN_TYPE_REFERENCE) :
                JACKSON.streamingSerializerDeserializer(Boolean.class);
    }

    private static SerializerDeserializer<Integer> intSerializer(boolean typeRef) {
        return typeRef ? JACKSON.serializerDeserializer(INTEGER_TYPE_REFERENCE) :
                JACKSON.serializerDeserializer(Integer.class);
    }

    private static StreamingSerializerDeserializer<Integer> intStreamingSerializer(boolean typeRef) {
        return typeRef ? JACKSON.streamingSerializerDeserializer(INTEGER_TYPE_REFERENCE) :
                JACKSON.streamingSerializerDeserializer(Integer.class);
    }
}
