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
import io.servicetalk.data.protobuf.test.TestProtos.DummyMessage;
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
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
import static org.hamcrest.Matchers.sameInstance;

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

    @Test
    void testProtobufV2Compatibility() {
        SerializerDeserializer<Protobuf2Message> serializerDeserializer =
                ProtobufSerializerFactory.PROTOBUF.serializerDeserializer(Protobuf2Message.class);

        Buffer dummyBuffer = DEFAULT_ALLOCATOR.newBuffer();
        Protobuf2Message deserialized = serializerDeserializer.deserialize(dummyBuffer, DEFAULT_ALLOCATOR);
        assertThat(deserialized, sameInstance(Protobuf2Message.MESSAGE));
    }

    @SuppressWarnings("PMD.MutableStaticState")
    public static class Protobuf2Message implements MessageLite {

        static final Protobuf2Message MESSAGE = new Protobuf2Message();
        public static Parser<Protobuf2Message> PARSER = new Parser<Protobuf2Message>() {

            @Override
            public Protobuf2Message parseFrom(final CodedInputStream input) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseFrom(final CodedInputStream input,
                                              final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialFrom(final CodedInputStream input) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialFrom(final CodedInputStream input,
                                                     final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseFrom(final ByteBuffer data) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseFrom(final ByteBuffer data, final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseFrom(final ByteString data) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseFrom(final ByteString data, final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialFrom(final ByteString data) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialFrom(final ByteString data,
                                                     final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseFrom(final byte[] data, final int off, final int len)
                    throws InvalidProtocolBufferException {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseFrom(final byte[] data, final int off, final int len,
                                              final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseFrom(final byte[] data) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseFrom(final byte[] data, final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialFrom(final byte[] data, final int off, final int len) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialFrom(final byte[] data, final int off, final int len,
                                                     final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialFrom(final byte[] data) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialFrom(final byte[] data, final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseFrom(final InputStream input) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseFrom(final InputStream input, final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialFrom(final InputStream input) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialFrom(final InputStream input,
                                                     final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseDelimitedFrom(final InputStream input) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parseDelimitedFrom(final InputStream input,
                                                       final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialDelimitedFrom(final InputStream input) {
                return MESSAGE;
            }

            @Override
            public Protobuf2Message parsePartialDelimitedFrom(final InputStream input,
                                                              final ExtensionRegistryLite extensionRegistry) {
                return MESSAGE;
            }
        };

        @Override
        public void writeTo(final CodedOutputStream output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getSerializedSize() {
            return 0;
        }

        @Override
        public Parser<? extends MessageLite> getParserForType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteString toByteString() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] toByteArray() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTo(final OutputStream output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeDelimitedTo(final OutputStream output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Builder newBuilderForType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Builder toBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MessageLite getDefaultInstanceForType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isInitialized() {
            return false;
        }
    }
}
