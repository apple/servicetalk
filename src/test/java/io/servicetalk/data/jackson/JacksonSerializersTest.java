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
import io.servicetalk.concurrent.api.BlockingIterator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.data.jackson.JacksonSerializers.deserializer;
import static io.servicetalk.data.jackson.JacksonSerializers.serializer;
import static io.servicetalk.data.jackson.TestPojo.verifyExpected1And2;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class JacksonSerializersTest {
    @Rule
    public final ExpectedException expectedException = ExpectedException.none();
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private JsonFactory jsonFactory;
    private ObjectMapper objectMapper;

    @Before
    public void setup() {
        jsonFactory = new JsonFactory();
        objectMapper = new ObjectMapper(jsonFactory);
    }

    @Test
    public void streamInvalidDataForDeserialize() throws ExecutionException, InterruptedException {
        TestPojo expected = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null, new String[] {"bar"},
                null);
        Publisher<Buffer> bufferPublisher = serializer(objectMapper.writerFor(TestPojo.class),
                DEFAULT_ALLOCATOR).apply(just(expected));
        Buffer buf = awaitIndefinitelyNonNull(bufferPublisher.first());
        buf.setByte(buf.getWriterIndex() - 1, buf.getByte(buf.getWriterIndex() - 1) + 1);

        bufferPublisher = just(buf);
        Publisher<TestPojo> pojoPublisher = deserializer(jsonFactory, objectMapper, TestPojo.class)
                .apply(bufferPublisher);
        expectedException.expect(ExecutionException.class);
        expectedException.expectCause(instanceOf(JsonParseException.class));
        awaitIndefinitelyNonNull(pojoPublisher.first());
    }

    @Test
    public void streamNoItem() {
        Publisher<TestPojo> pojoRequest = deserializer(jsonFactory, objectMapper, TestPojo.class)
                .apply(serializer(objectMapper, TestPojo.class, DEFAULT_ALLOCATOR).apply(empty()));
        BlockingIterator<TestPojo> pojoItr = pojoRequest.toIterable().iterator();
        assertFalse(pojoItr.hasNext());
    }

    @Test
    public void streamOneItem() throws ExecutionException, InterruptedException {
        TestPojo expected = new TestPojo(true, Byte.MAX_VALUE, Short.MAX_VALUE, Character.MAX_VALUE, Integer.MIN_VALUE,
                Long.MAX_VALUE, Float.MAX_VALUE, Double.MAX_VALUE, "foo", new String[] {"bar", "baz"}, null);
        Publisher<TestPojo> pojoRequest = deserializer(jsonFactory, objectMapper, TestPojo.class).apply(
                                    serializer(objectMapper, TestPojo.class, DEFAULT_ALLOCATOR).apply(just(expected)));
        TestPojo actual = awaitIndefinitelyNonNull(pojoRequest.first());
        assertEquals(expected, actual);
    }

    @Test
    public void streamTwoItems() {
        TestPojo expected1 = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null,
                new String[] {"bar", "baz"}, null);
        TestPojo expected2 = new TestPojo(false, (byte) 500, (short) 353, 'r', 100, 534, 33.25f, 888.5, null,
                new String[] {"foo"}, expected1);
        Publisher<TestPojo> pojoRequest = deserializer(jsonFactory, objectMapper, TestPojo.class).apply(
                serializer(objectMapper, TestPojo.class, DEFAULT_ALLOCATOR).apply(from(expected1, expected2)));
        verifyExpected1And2(expected1, expected2, pojoRequest.toIterable().iterator());
    }

    @Test
    public void streamBufferSplitAcrossMultipleDecodes() throws ExecutionException, InterruptedException {
        TestPojo expected1 = new TestPojo(true, (byte) -2, (short) -3, 'a', 2, 5, 3.2f, -8.5, null,
                new String[] {"bar", "baz"}, null);
        TestPojo expected2 = new TestPojo(false, (byte) 500, (short) 353, 'r', 100, 534, 33.25f, 888.5, null,
                new String[] {"foo"}, expected1);
        Publisher<Buffer> serializedRequest1 =
                serializer(objectMapper, TestPojo.class, DEFAULT_ALLOCATOR).apply(just(expected1));
        Publisher<Buffer> serializedRequest2 =
                serializer(objectMapper, TestPojo.class, DEFAULT_ALLOCATOR).apply(just(expected2));

        Buffer req1Buffer = awaitIndefinitelyNonNull(serializedRequest1.first());
        Buffer req2Buffer = awaitIndefinitelyNonNull(serializedRequest2.first());

        // Chunk each of the previous buffers into a single byte buffer for maximum-splitting.
        Buffer[] chunks = new Buffer[req1Buffer.getReadableBytes() + req2Buffer.getReadableBytes()];
        int chunkIndex = 0;
        for (int i = req1Buffer.getReaderIndex(); i < req1Buffer.getWriterIndex(); ++i) {
            chunks[chunkIndex++] = DEFAULT_ALLOCATOR.newBuffer(1).writeByte(req1Buffer.getByte(i));
        }
        for (int i = req2Buffer.getReaderIndex(); i < req2Buffer.getWriterIndex(); ++i) {
            chunks[chunkIndex++] = DEFAULT_ALLOCATOR.newBuffer(1).writeByte(req2Buffer.getByte(i));
        }

        Publisher<TestPojo> pojoRequest = deserializer(jsonFactory, objectMapper, TestPojo.class).apply(from(chunks));

        verifyExpected1And2(expected1, expected2, pojoRequest.toIterable().iterator());
    }
}
