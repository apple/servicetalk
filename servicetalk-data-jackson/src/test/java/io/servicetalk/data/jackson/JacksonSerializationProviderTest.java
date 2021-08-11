/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.EmptyBuffer;
import io.servicetalk.serialization.api.SerializationException;
import io.servicetalk.serialization.api.StreamingDeserializer;
import io.servicetalk.serialization.api.TypeHolder;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Deprecated
class JacksonSerializationProviderTest {
    private final JacksonSerializationProvider serializationProvider = new JacksonSerializationProvider();

    @Test
    void testFromClass() {
        TestPojo expected = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null, new String[] {"bar"},
                null);

        final Buffer serialized = DEFAULT_ALLOCATOR.newBuffer();
        serializationProvider.getSerializer(TestPojo.class).serialize(expected, serialized);
        final Iterator<TestPojo> iterator = serializationProvider.getDeserializer(TestPojo.class)
                .deserialize(serialized).iterator();
        assertTrue(iterator.hasNext());
        assertEquals(expected, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    void testFromTypeHolder() {
        TypeHolder<List<TestPojo>> listTypeHolder = new TypeHolder<List<TestPojo>>() { };

        TestPojo expected = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null, new String[] {"bar"},
                null);

        List<TestPojo> pojos = singletonList(expected);

        final Buffer serialized = DEFAULT_ALLOCATOR.newBuffer();
        serializationProvider.getSerializer(listTypeHolder).serialize(pojos, serialized);
        final Iterator<List<TestPojo>> iterator = serializationProvider.getDeserializer(listTypeHolder)
                .deserialize(serialized).iterator();
        assertTrue(iterator.hasNext());
        assertEquals(pojos, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    void deserializeInvalidData() {
        TestPojo expected = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null, new String[] {"bar"},
                null);
        final Buffer serialized = serializePojo(expected);
        serialized.setByte(serialized.writerIndex() - 1, serialized.getByte(serialized.writerIndex() - 1) + 1);

        final StreamingDeserializer<TestPojo> deserializer = serializationProvider.getDeserializer(TestPojo.class);
        try {
            deserializer.deserialize(serialized);
            fail();
        } catch (SerializationException e) {
            assertThat("Unexpected exception", e.getCause(), instanceOf(JsonParseException.class));
        }
        assertThat("Unexpected data remaining in deserializer", deserializer.hasData(), is(true));
    }

    @Test
    void deserializeEmpty() {
        final StreamingDeserializer<TestPojo> deserializer = serializationProvider.getDeserializer(TestPojo.class);
        Iterable<TestPojo> pojos = deserializer.deserialize(EmptyBuffer.EMPTY_BUFFER);
        Iterator<TestPojo> pojoItr = pojos.iterator();
        assertFalse(pojoItr.hasNext());
        assertThat("Unexpected data remaining in deserializer", deserializer.hasData(), is(false));
    }

    @Test
    void deserializeSingleItem() {
        TestPojo expected = new TestPojo(true, Byte.MAX_VALUE, Short.MAX_VALUE, Character.MAX_VALUE, Integer.MIN_VALUE,
                Long.MAX_VALUE, Float.MAX_VALUE, Double.MAX_VALUE, "foo", new String[] {"bar", "baz"}, null);
        final StreamingDeserializer<TestPojo> deserializer = serializationProvider.getDeserializer(TestPojo.class);
        Iterator<TestPojo> pojos = deserializer.deserialize(serializePojo(expected)).iterator();
        assertTrue(pojos.hasNext());
        assertEquals(expected, pojos.next());
        assertFalse(pojos.hasNext());
        assertThat("Unexpected data remaining in deserializer", deserializer.hasData(), is(false));
    }

    @Test
    void deserializeTwoItemsFromTwoBuffers() {
        TestPojo expected1 = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null,
                new String[] {"bar", "baz"}, null);
        TestPojo expected2 = new TestPojo(false, (byte) 500, (short) 353, 'r', 100, 534, 33.25f, 888.5, null,
                new String[] {"foo"}, expected1);
        final StreamingDeserializer<TestPojo> deserializer = serializationProvider.getDeserializer(TestPojo.class);

        Iterator<TestPojo> firstIterator = deserializer.deserialize(serializePojo(expected1)).iterator();
        assertTrue(firstIterator.hasNext());
        assertEquals(expected1, firstIterator.next());
        assertFalse(firstIterator.hasNext());

        Iterator<TestPojo> secondIterator = deserializer.deserialize(serializePojo(expected2)).iterator();
        assertTrue(secondIterator.hasNext());
        assertEquals(expected2, secondIterator.next());
        assertFalse(secondIterator.hasNext());
        assertThat("Unexpected data remaining in deserializer", deserializer.hasData(), is(false));
    }

    @Test
    void deserializeTwoItemsFromSingleBuffer() {
        TestPojo expected1 = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null,
                new String[] {"bar", "baz"}, null);
        TestPojo expected2 = new TestPojo(false, (byte) 500, (short) 353, 'r', 100, 534, 33.25f, 888.5, null,
                new String[] {"foo"}, expected1);

        final Buffer buffer1 = serializePojo(expected1);
        final Buffer buffer2 = serializePojo(expected2);

        Buffer composite = DEFAULT_ALLOCATOR.newBuffer(buffer1.readableBytes() + buffer2.readableBytes());
        composite.writeBytes(buffer1).writeBytes(buffer2);

        final StreamingDeserializer<TestPojo> deserializer = serializationProvider.getDeserializer(TestPojo.class);

        Iterator<TestPojo> iter = deserializer.deserialize(composite).iterator();
        assertTrue(iter.hasNext());
        assertEquals(expected1, iter.next());
        assertTrue(iter.hasNext());
        final TestPojo next = iter.next();
        assertEquals(expected2, next);
        assertFalse(iter.hasNext());

        assertThat("Unexpected data remaining in deserializer", deserializer.hasData(), is(false));
    }

    @Test
    void deserializeSplitAcrossMultipleBuffers() {
        TestPojo expected1 = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null,
                new String[] {"bar", "baz"}, null);
        TestPojo expected2 = new TestPojo(false, (byte) 500, (short) 353, 'r', 100, 534, 33.25f, 888.5, null,
                new String[] {"foo"}, expected1);

        Buffer req1Buffer = serializePojo(expected1);
        Buffer req2Buffer = serializePojo(expected2);

        final StreamingDeserializer<TestPojo> deserializer = serializationProvider.getDeserializer(TestPojo.class);

        deserializeChunks(expected1, req1Buffer, deserializer);

        deserializeChunks(expected2, req2Buffer, deserializer);

        assertThat("Unexpected data remaining in deserializer", deserializer.hasData(), is(false));
    }

    @Test
    void deserializeIncompleteBufferAsAggregated() {
        TestPojo expected = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null, new String[] {"bar"},
                null);
        final Buffer buffer = serializePojo(expected);
        final StreamingDeserializer<TestPojo> deSerializer = serializationProvider.getDeserializer(TestPojo.class);
        deSerializer.deserialize(buffer.readBytes(buffer.readableBytes() - 1));
        try {
            deSerializer.close();
            fail();
        } catch (SerializationException e) {
            // expected
        }
    }

    @Test
    void testParseOnlyValueString() {
        String json = "\"x\"";
        final Buffer buffer = DEFAULT_ALLOCATOR.fromAscii(json);
        final StreamingDeserializer<String> deSerializer = serializationProvider.getDeserializer(String.class);
        final Iterator<String> deserialized = deSerializer.deserialize(buffer).iterator();
        assertThat("No value deserialized.", deserialized.hasNext(), is(true));
        assertThat("Unexpected value deserialized.", deserialized.next(), is("x"));
        deSerializer.close();
    }

    @Test
    void testParseOnlyValueNull() {
        String json = "null";
        final Buffer buffer = DEFAULT_ALLOCATOR.fromAscii(json);
        final StreamingDeserializer<String> deSerializer = serializationProvider.getDeserializer(String.class);
        final Iterator<String> deserialized = deSerializer.deserialize(buffer).iterator();
        assertThat("Null deserialized into an item.", deserialized.hasNext(), is(false));
        deSerializer.close();
    }

    @Disabled("Jackson currently does not support parsing only boolean value.")
    @Test
    void testParseOnlyValueBoolean() {
        String json = "true";
        final Buffer buffer = DEFAULT_ALLOCATOR.fromAscii(json);
        final StreamingDeserializer<Boolean> deSerializer = serializationProvider.getDeserializer(Boolean.class);
        final Iterator<Boolean> deserialized = deSerializer.deserialize(buffer).iterator();
        assertThat("No value deserialized.", deserialized.hasNext(), is(true));
        assertThat("Unexpected value deserialized.", deserialized.next(), is(true));
        deSerializer.close();
    }

    @Disabled("Jackson currently does not support parsing only number value.")
    @Test
    void testParseOnlyValueInt() {
        String json = "1";
        final Buffer buffer = DEFAULT_ALLOCATOR.fromAscii(json);
        final StreamingDeserializer<Integer> deSerializer = serializationProvider.getDeserializer(Integer.class);
        final Iterator<Integer> deserialized = deSerializer.deserialize(buffer).iterator();
        assertThat("No value deserialized.", deserialized.hasNext(), is(true));
        assertThat("Unexpected value deserialized.", deserialized.next(), is(1));
        deSerializer.close();
    }

    private void deserializeChunks(final TestPojo expected1, final Buffer req1Buffer,
                                   final StreamingDeserializer<TestPojo> deSerializer) {
        for (int i = req1Buffer.readerIndex(); i < req1Buffer.writerIndex(); ++i) {
            Buffer buffer = DEFAULT_ALLOCATOR.newBuffer(1).writeByte(req1Buffer.getByte(i));
            final Iterator<TestPojo> iter = deSerializer.deserialize(buffer).iterator();
            if (i == req1Buffer.writerIndex() - 1) {
                assertTrue(iter.hasNext());
                assertEquals(expected1, iter.next());
            } else {
                assertFalse(iter.hasNext());
            }
        }
    }

    @Nonnull
    private Buffer serializePojo(final TestPojo expected) {
        final Buffer serialized = DEFAULT_ALLOCATOR.newBuffer();
        serializationProvider.serialize(expected, serialized);
        return serialized;
    }
}
