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
import io.servicetalk.serialization.api.StreamingDeserializer;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.grpc.api.GrpcMessageEncoding.None;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProtoDeserializerTest {

    @SuppressWarnings("unchecked")
    private final Parser<DummyMessage> parser = Mockito.mock(Parser.class);
    private final ProtoBufSerializationProvider<DummyMessage> serializationProvider =
            new ProtoBufSerializationProvider<>(DummyMessage.class, None, parser);

    @Before
    public void setUp() throws Exception {
        when(parser.parseFrom(any(InputStream.class))).thenAnswer(invocation -> {
            InputStream is = invocation.getArgument(0);
            StringBuilder msg = new StringBuilder();
            byte[] data = new byte[32];
            int read;
            try {
                while ((read = is.read(data)) > 0) {
                    msg.append(new String(data, 0, read, StandardCharsets.US_ASCII));
                }
            } finally {
                is.close();
            }
            DummyMessage dummyMessage = mock(DummyMessage.class);
            when(dummyMessage.getMessage()).thenReturn(msg.toString());
            return dummyMessage;
        });
    }

    @Test
    public void singleMessageAligned() {
        List<String> deserialized = deserialize(grpcBufferFor("Hello"));
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello"));
    }

    @Test
    public void singleMessageAlignedAsIterable() {
        List<String> deserialized = deserialize(new Buffer[]{grpcBufferFor("Hello")});
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello"));
    }

    @Test
    public void multipleMessagesAligned() {
        List<String> deserialized = deserialize(grpcBufferFor("Hello1"), grpcBufferFor("Hello2"));
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello1", "Hello2"));
    }

    @Test
    public void multipleMessagesInSingleBuffer() {
        List<String> deserialized = deserialize(grpcBufferFor("Hello1", "Hello2"));
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello1", "Hello2"));
    }

    @Test
    public void splitMessageInBuffers() {
        Buffer msg = grpcBufferFor("Hello");
        List<Buffer> buffers = new ArrayList<>();
        while (msg.readableBytes() > 0) {
            buffers.add(msg.readSlice(1));
        }
        List<String> deserialized = deserialize(buffers.toArray(new Buffer[0]));
        assertThat("Unexpected messages deserialized.", deserialized, contains("Hello"));
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

    private Buffer grpcBufferFor(final String... messages) {
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer();
        for (String message : messages) {
            byte[] data = message.getBytes(StandardCharsets.US_ASCII);
            buffer.writeByte(0).writeInt(data.length).writeBytes(data);
        }
        return buffer;
    }

    private interface DummyMessage extends MessageLite {

        String getMessage();
    }
}
