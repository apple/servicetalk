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
package io.servicetalk.data.protobuf;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.data.protobuf.test.TestProtos.DummyMessage;
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.serialization.api.TypeHolder;

import com.google.protobuf.Parser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.util.Collections.unmodifiableList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.internal.util.collections.Iterables.firstOf;

class ProtobufSerializationProviderTest {

    private final ProtobufSerializationProvider provider = new ProtobufSerializationProvider();

    private final DummyMessage testMessage = DummyMessage.newBuilder().setMessage("test").build();
    private final byte[] testMessageBytes = testMessage.toByteArray();
    private final TypeHolder<DummyMessage> typeHolder = new TypeHolder<DummyMessage>() { };

    @Test
    void serializeMessageByClass() {
        Buffer buffer = newBuffer();
        provider.getSerializer(DummyMessage.class).serialize(testMessage, buffer);
        assertThat(toBytes(buffer), equalTo(testMessageBytes));
    }

    @Test
    void serializeMessageByType() {
        Buffer buffer = newBuffer();
        provider.getSerializer(typeHolder).serialize(testMessage, buffer);
        assertThat(toBytes(buffer), equalTo(testMessageBytes));
    }

    @Test
    void invalidSerializerClassException() {
        assertThrows(SerializationException.class, () -> provider.getSerializer(String.class));
    }

    @Test
    void findParserWithReflection() {
        Parser<DummyMessage> parser = ProtobufSerializationProvider.reflectionParserFor(DummyMessage.class);
        assertThat(parser, sameInstance(testMessage.getParserForType()));
    }

    @Test
    void deserializeMessageByClass() {
        Buffer buffer = wrap(testMessageBytes);
        DummyMessage message = firstOf(provider.getDeserializer(DummyMessage.class).deserialize(buffer));
        assertThat(message, equalTo(testMessage));
    }

    @Test
    void deserializeMessageByType() {
        Buffer buffer = wrap(testMessageBytes);
        DummyMessage message = firstOf(provider.getDeserializer(typeHolder).deserialize(buffer));
        assertThat(message, equalTo(testMessage));
    }

    @Test
    void invalidDeserializerClassException() {
        assertThrows(SerializationException.class, () -> provider.getDeserializer(String.class));
    }

    @Test
    void deserializeExceptionTruncatedBytes() {
        Buffer badBuffer = newBuffer().writeBytes(testMessageBytes, 0, testMessageBytes.length / 2);
        assertThrows(SerializationException.class,
                () -> provider.getDeserializer(DummyMessage.class).deserialize(badBuffer));
    }

    @Test
    void deserializeDoubledBytes() {
        DummyMessage overwrite = DummyMessage.newBuilder().setMessage("supplanted").build();
        Buffer buffer = newBuffer().writeBytes(testMessageBytes).writeBytes(overwrite.toByteArray());
        List<DummyMessage> messages = toList(provider.getDeserializer(DummyMessage.class).deserialize(buffer));
        assertThat(messages, hasSize(1));
        assertThat(messages.get(0), equalTo(overwrite));
    }

    @Test
    void deserializeEmptyBuffer() {
        DummyMessage emptyMessage = DummyMessage.newBuilder().build();
        DummyMessage message = firstOf(provider.getDeserializer(DummyMessage.class).deserialize(newBuffer()));
        assertThat(message, equalTo(emptyMessage));
    }

    @Test
    void cacheParsers() {
        AtomicInteger providerCounter = new AtomicInteger(0);
        ProtobufSerializationProvider p = new ProtobufSerializationProvider(aClass -> {
            providerCounter.getAndIncrement();
            return DummyMessage.parser();
        });
        assertThat(providerCounter.get(), equalTo(0));
        p.getDeserializer(DummyMessage.class);
        assertThat(providerCounter.get(), equalTo(1));
        p.getDeserializer(DummyMessage.class);
        assertThat(providerCounter.get(), equalTo(1));
    }

    // ========================================================================

    private static Buffer newBuffer() {
        return DEFAULT_ALLOCATOR.newBuffer();
    }

    private static Buffer wrap(byte[] bytes) {
        return DEFAULT_ALLOCATOR.wrap(bytes);
    }

    private static byte[] toBytes(Buffer buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    private static <T> List<T> toList(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        iterable.forEach(list::add);
        return unmodifiableList(list);
    }
}
