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
package io.servicetalk.data.protobuf;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.data.protobuf.test.TestProtos.DummyMessage;
import io.servicetalk.data.protobuf.test.TestProtos.MapMessage;
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static io.servicetalk.buffer.api.Buffer.asInputStream;
import static io.servicetalk.buffer.api.Buffer.asOutputStream;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.data.protobuf.ProtobufSerializerFactory.PROTOBUF;
import static io.servicetalk.data.protobuf.test.TestProtos.DummyMessage.parser;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

class ProtobufSerializerFactoryTest {
    private static final List<Arguments> POJOS = Arrays.asList(
            Arguments.of(singletonList(newMsg("hello"))),
            Arguments.of(asList(newMsg("hello"), newMsg("world"))),
            Arguments.of(asList(newMsg("hello"), newMsg("world"), newMsg("!"))),
            Arguments.of(asList(newMsg("hello"), newMsg(1 << 7))),
            Arguments.of(asList(newMsg(1 << 14), newMsg("!"))),
            Arguments.of(singletonList(newMsg(1 << 21))),
            Arguments.of(singletonList(newMsg(1 << 28)))
    );

    @Test
    void serializeDeserialize() {
        final DummyMessage testMessage = DummyMessage.newBuilder().setMessage("test").build();
        serializeDeserialize(testMessage, PROTOBUF.serializerDeserializer(DummyMessage.parser()));
    }

    @Test
    void serializeDeserializeClass() {
        final DummyMessage testMessage = DummyMessage.newBuilder().setMessage("test").build();
        serializeDeserialize(testMessage, PROTOBUF.serializerDeserializer(DummyMessage.class));
    }

    @Test
    void serializeEmptyMap() throws InvalidProtocolBufferException {
        // These bytes were serialized from scala protobuf and don't include the trailing empty map value.
        final byte[] emptyMessageMapEmptyValueBytes = {0xa, 0x5, 0xa, 0x3, 0x66, 0x6f, 0x6f};
        final BufferAllocator allocator = DEFAULT_ALLOCATOR;
        final Buffer buffer = allocator.wrap(emptyMessageMapEmptyValueBytes);
        final MapMessage mapMessage = MapMessage.parseFrom(emptyMessageMapEmptyValueBytes);
        SerializerDeserializer<MapMessage> serializer = PROTOBUF.serializerDeserializer(MapMessage.class);
        assertThat(serializer.deserialize(buffer, allocator), equalTo(mapMessage));
        // Serializing in java currently writes "0x12, 0x0" for "map value tag, empty string" so we can't compare number
        // of bytes directly, but we can ensure it deserializes back to an equal object.
        assertThat(serializer.deserialize(serializer.serialize(mapMessage, allocator), allocator), equalTo(mapMessage));
    }

    private static void serializeDeserialize(final DummyMessage testMessage,
                                             final SerializerDeserializer<DummyMessage> serializer) {
        final byte[] testMessageBytes = testMessage.toByteArray();
        Buffer buffer = serializer.serialize(testMessage, DEFAULT_ALLOCATOR);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        assertThat(bytes, equalTo(testMessageBytes));
        assertThat(serializer.deserialize(buffer, DEFAULT_ALLOCATOR), equalTo(testMessage));
    }

    @ParameterizedTest(name = "pojos={0}")
    @MethodSource("pojos")
    void streamingWriteDelimitedToDeserialized(Collection<DummyMessage> msgs) throws Exception {
        streamingWriteDelimitedToDeserialized(PROTOBUF.streamingSerializerDeserializer(DummyMessage.parser()), msgs);
    }

    @ParameterizedTest(name = "pojos={0}")
    @MethodSource("pojos")
    void streamingWriteDelimitedToDeserializedClass(Collection<DummyMessage> msgs) throws Exception {
        streamingWriteDelimitedToDeserialized(PROTOBUF.streamingSerializerDeserializer(DummyMessage.class), msgs);
    }

    private static void streamingWriteDelimitedToDeserialized(StreamingSerializerDeserializer<DummyMessage> serializer,
                                                              Collection<DummyMessage> msgs) throws Exception {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer();
        OutputStream os = asOutputStream(buffer);
        for (DummyMessage msg : msgs) {
            msg.writeDelimitedTo(os);
        }

        assertThat(serializer.deserialize(from(buffer), DEFAULT_ALLOCATOR).toFuture().get(), contains(msgs.toArray()));
    }

    @ParameterizedTest(name = "pojos={0}")
    @MethodSource("pojos")
    void streamingParseDelimitedFromSerialized(Collection<DummyMessage> msgs) throws Exception {
        streamingParseDelimitedFromSerialized(msgs, PROTOBUF.streamingSerializerDeserializer(parser()));
    }

    @ParameterizedTest(name = "pojos={0}")
    @MethodSource("pojos")
    void streamingParseDelimitedFromSerializedClass(Collection<DummyMessage> msgs) throws Exception {
        streamingParseDelimitedFromSerialized(msgs, PROTOBUF.streamingSerializerDeserializer(DummyMessage.class));
    }

    private static void streamingParseDelimitedFromSerialized(
            Collection<DummyMessage> msgs, StreamingSerializerDeserializer<DummyMessage> serializer) throws Exception {
        Collection<Buffer> serialized = serializer.serialize(fromIterable(msgs), DEFAULT_ALLOCATOR)
                .toFuture().get();

        Parser<DummyMessage> parser = parser();
        Collection<DummyMessage> deserialized = new ArrayList<>(serialized.size());
        for (Buffer buf : serialized) {
            deserialized.add(parser.parseDelimitedFrom(asInputStream(buf)));
        }
        assertThat(deserialized, contains(msgs.toArray()));
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> pojos() {
        return POJOS.stream();
    }

    private static DummyMessage newMsg(String msg) {
        return DummyMessage.newBuilder().setMessage(msg).build();
    }

    private static DummyMessage newMsg(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; ++i) {
            sb.append('a');
        }
        return newMsg(sb.toString());
    }
}
