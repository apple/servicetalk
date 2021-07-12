/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.protobuf;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.serialization.api.StreamingDeserializer;

import com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.grpc.protobuf.test.TestProtos.DummyMessage;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class ProtoDeserializerTest {

    private final Parser<DummyMessage> parser = DummyMessage.parser();
    private final ProtoBufSerializationProvider<DummyMessage> serializationProvider =
            new ProtoBufSerializationProvider<>(DummyMessage.class, identity(), parser);

    @Test
    void zeroLengthMessageAligned() throws IOException {
        List<String> deserialized = deserialize(grpcBufferFor(new String[]{null}));
        assertThat("Unexpected messages deserialized.", deserialized, contains(""));
    }

    @Test
    void zeroLengthFirstMessageAligned() throws IOException {
        List<String> deserialized = deserialize(grpcBufferFor(null, "Hello"));
        assertThat("Unexpected messages deserialized.", deserialized, contains("", "Hello"));
    }

    @Test
    void zeroLengthLastMessageAligned() throws IOException {
        List<String> deserialized = deserialize(grpcBufferFor("Hello", null));
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello", ""));
    }

    @Test
    void zeroLengthMiddleMessageAligned() throws IOException {
        List<String> deserialized = deserialize(grpcBufferFor("Hello1", null, "Hello2"));
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello1", "", "Hello2"));
    }

    @Test
    void singleMessageAligned() throws IOException {
        List<String> deserialized = deserialize(grpcBufferFor("Hello"));
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello"));
    }

    @Test
    void singleMessageAlignedAsIterable() throws IOException {
        List<String> deserialized = deserialize(new Buffer[]{grpcBufferFor("Hello")});
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello"));
    }

    @Test
    void multipleMessagesAligned() throws IOException {
        List<String> deserialized = deserialize(grpcBufferFor("Hello1"), grpcBufferFor("Hello2"));
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello1", "Hello2"));
    }

    @Test
    void multipleMessagesInSingleBuffer() throws IOException {
        List<String> deserialized = deserialize(grpcBufferFor("Hello1", "Hello2"));
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello1", "Hello2"));
    }

    @Test
    void splitMessageInBuffers() throws IOException {
        Buffer msg = grpcBufferFor("Hello");
        List<Buffer> buffers = new ArrayList<>();
        while (msg.readableBytes() > 0) {
            buffers.add(msg.readSlice(1));
        }
        List<String> deserialized = deserialize(buffers.toArray(new Buffer[0]));
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello"));
    }

    @Test
    void multipleMessagesInCompositeBuffer() throws IOException {
        final CompositeBuffer composite = DEFAULT_ALLOCATOR.newCompositeBuffer();
        Buffer msg = grpcBufferFor("Hello");
        while (msg.readableBytes() > 0) {
            composite.addBuffer(msg.readSlice(1));
        }
        composite.addBuffer(grpcBufferFor("Hello1"));
        List<String> deserialized = deserialize(composite);
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello", "Hello1"));
    }

    private List<String> deserialize(Buffer buffer) {
        return deserialize(deserializer -> deserializer.deserialize(buffer));
    }

    private List<String> deserialize(Buffer... buffer) {
        return deserialize(deserializer -> deserializer.deserialize(asList(buffer)));
    }

    private List<String> deserialize(
            final Function<StreamingDeserializer<DummyMessage>, Iterable<DummyMessage>> deserializeFunction) {
        StreamingDeserializer<DummyMessage> deserializer = serializationProvider.getDeserializer(DummyMessage.class);
        return stream(deserializeFunction.apply(deserializer).spliterator(), false)
                .map(DummyMessage::getMessage).collect(toList());
    }

    private Buffer grpcBufferFor(final String... messages) throws IOException {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer();
        OutputStream out = Buffer.asOutputStream(buffer);
        for (String message : messages) {
            DummyMessage.Builder builder = DummyMessage.newBuilder();
            if (message != null) {
                builder.setMessage(message);
            }
            DummyMessage msg = builder.build();
            buffer.writeByte(0); // no compression
            buffer.writeInt(msg.getSerializedSize());
            msg.writeTo(out);
        }
        return buffer;
    }
}
