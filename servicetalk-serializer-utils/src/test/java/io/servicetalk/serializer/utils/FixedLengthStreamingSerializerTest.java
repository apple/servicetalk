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

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.serializer.utils.StringSerializer.stringSerializer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixedLengthStreamingSerializerTest {
    @Test
    void serializeDeserialize() throws Exception {
        FixedLengthStreamingSerializer<String> serializer = new FixedLengthStreamingSerializer<>(
                stringSerializer(UTF_8), String::length);

        assertThat(serializer.deserialize(serializer.serialize(from("foo", "bar"), DEFAULT_ALLOCATOR),
                DEFAULT_ALLOCATOR).toFuture().get(), contains("foo", "bar"));
    }

    @Test
    void frameAtLimitDeserializes() throws Exception {
        FixedLengthStreamingSerializer<String> serializer = new FixedLengthStreamingSerializer<>(
                stringSerializer(UTF_8), String::length, 3);

        assertThat(serializer.deserialize(serializer.serialize(from("foo", "bar"), DEFAULT_ALLOCATOR),
                DEFAULT_ALLOCATOR).toFuture().get(), contains("foo", "bar"));
    }

    @Test
    void emptyFrameDeserializes() throws Exception {
        FixedLengthStreamingSerializer<String> serializer = new FixedLengthStreamingSerializer<>(
                stringSerializer(UTF_8), String::length, 8);
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer().writeInt(0);

        assertThat(serializer.deserialize(from(buffer), DEFAULT_ALLOCATOR).toFuture().get(), contains(""));
    }

    @Test
    void frameAboveLimitSingleBufferRejected() {
        FixedLengthStreamingSerializer<String> serializer = new FixedLengthStreamingSerializer<>(
                stringSerializer(UTF_8), String::length, 8);
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer().writeInt(9);

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> serializer.deserialize(from(buffer), DEFAULT_ALLOCATOR).toFuture().get());
        assertThat(e.getCause(), instanceOf(SerializationException.class));
    }

    @Test
    void frameAboveLimitSplitAcrossBuffersRejected() {
        FixedLengthStreamingSerializer<String> serializer = new FixedLengthStreamingSerializer<>(
                stringSerializer(UTF_8), String::length, 8);
        Buffer first = DEFAULT_ALLOCATOR.newBuffer().writeShort(0);
        Buffer second = DEFAULT_ALLOCATOR.newBuffer().writeShort(9);

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> serializer.deserialize(from(first, second), DEFAULT_ALLOCATOR).toFuture().get());
        assertThat(e.getCause(), instanceOf(SerializationException.class));
    }

    @Test
    void zeroLimitDisablesCheck() throws Exception {
        FixedLengthStreamingSerializer<String> serializer = new FixedLengthStreamingSerializer<>(
                stringSerializer(UTF_8), String::length, 0);
        Buffer buffer = DEFAULT_ALLOCATOR.newBuffer().writeInt(6).writeBytes("foobar".getBytes(UTF_8));

        assertThat(serializer.deserialize(from(buffer), DEFAULT_ALLOCATOR).toFuture().get(), contains("foobar"));
    }
}
