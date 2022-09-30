/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.encoding.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.encoding.netty.NettyCompression.deflateDefault;
import static io.servicetalk.encoding.netty.NettyCompression.deflateDefaultStreaming;
import static io.servicetalk.encoding.netty.NettyCompression.gzipDefault;
import static io.servicetalk.encoding.netty.NettyCompression.gzipDefaultStreaming;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

class NettyCompressionStreamingSerializerTest {
    enum StreamingType {
        GZIP(gzipDefaultStreaming(), DEFAULT_ALLOCATOR),
        DEFLATE(deflateDefaultStreaming(), DEFAULT_ALLOCATOR),
        GZIP_RO(gzipDefaultStreaming(), DEFAULT_RO_ALLOCATOR),
        DEFLATE_RO(deflateDefaultStreaming(), DEFAULT_RO_ALLOCATOR);

        final StreamingSerializerDeserializer<Buffer> serializer;
        final BufferAllocator allocator;

        StreamingType(StreamingSerializerDeserializer<Buffer> serializer, BufferAllocator allocator) {
            this.serializer = serializer;
            this.allocator = allocator;
        }
    }

    enum AggType {
        GZIP(gzipDefault(), DEFAULT_ALLOCATOR),
        DEFLATE(deflateDefault(), DEFAULT_ALLOCATOR),
        GZIP_RO(gzipDefault(), DEFAULT_RO_ALLOCATOR),
        DEFLATE_RO(deflateDefault(), DEFAULT_RO_ALLOCATOR);

        final SerializerDeserializer<Buffer> serializer;
        final BufferAllocator allocator;

        AggType(SerializerDeserializer<Buffer> serializer, BufferAllocator allocator) {
            this.serializer = serializer;
            this.allocator = allocator;
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] type = {0}")
    @EnumSource(StreamingType.class)
    void streamingOverMultipleFrames(StreamingType type) throws Exception {
        BufferAllocator allocator = type.allocator;
        StreamingSerializerDeserializer<Buffer> serializer = type.serializer;
        String rawString = "hello";
        byte[] rawBytes = new byte[1024];
        ThreadLocalRandom.current().nextBytes(rawBytes);
        Buffer[] clearText = new Buffer[] {allocator.fromAscii(rawString), allocator.wrap(rawBytes)};
        Publisher<Buffer> serializedPub = serializer.serialize(from(clearText), DEFAULT_ALLOCATOR);

        CompositeBuffer serializedBuf = serializedPub
                .collect(DEFAULT_ALLOCATOR::newCompositeBuffer, CompositeBuffer::addBuffer).toFuture().get();

        assertThat(serializedBuf.readableBytes(), greaterThanOrEqualTo(4));
        CompositeBuffer result = serializer.deserialize(from(
                        // Copy so we feed in data from the allocator of choice.
                        allocator.wrap(readAndCopy(serializedBuf, 4)),
                        allocator.wrap(readAndCopy(serializedBuf, serializedBuf.readableBytes()))
                ), DEFAULT_ALLOCATOR)
                .collect(DEFAULT_ALLOCATOR::newCompositeBuffer, CompositeBuffer::addBuffer).toFuture().get();

        assertThat(result.readableBytes(), equalTo(rawString.length() + rawBytes.length));
        assertThat(result.readBytes(rawString.length()).toString(UTF_8), equalTo(rawString));
        assertThat(result, equalTo(allocator.wrap(rawBytes)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] type = {0}")
    @EnumSource(AggType.class)
    void aggregatedRoundTrip(AggType type) {
        BufferAllocator allocator = type.allocator;
        SerializerDeserializer<Buffer> serializer = type.serializer;
        byte[] rawBytes = new byte[1024];
        ThreadLocalRandom.current().nextBytes(rawBytes);
        Buffer serializedBuf = serializer.serialize(allocator.wrap(rawBytes), DEFAULT_ALLOCATOR);
        Buffer result = serializer.deserialize(
                allocator.wrap(readAndCopy(serializedBuf, serializedBuf.readableBytes())),
                DEFAULT_ALLOCATOR);

        assertThat(readAndCopy(result, result.readableBytes()), equalTo(rawBytes));
    }

    private static byte[] readAndCopy(Buffer buffer, int byteCount) {
        byte[] bytes = new byte[byteCount];
        buffer.readBytes(bytes);
        return bytes;
    }
}
