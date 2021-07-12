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
package io.servicetalk.encoding.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.encoding.api.CodecDecodingException;
import io.servicetalk.encoding.api.CodecEncodingException;
import io.servicetalk.encoding.api.ContentCodec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NettyChannelContentCodecTest {

    private static final String INPUT;
    static {
        byte[] arr = new byte[1024];
        Arrays.fill(arr, (byte) 'a');
        INPUT = new String(arr, US_ASCII);
    }

    private static Stream<ContentCodec> params() {
        return Stream.of(
                ContentCodings.gzipDefault(),
                ContentCodings.deflateDefault());
    }

    @ParameterizedTest
    @MethodSource("params")
    void testEncode(final ContentCodec codec) {
        testEncode(codec, DEFAULT_ALLOCATOR);
    }

    @ParameterizedTest
    @MethodSource("params")
    void testEncodeWithReadOnlyBuffer(final ContentCodec codec) {
        testEncode(codec, DEFAULT_RO_ALLOCATOR);
    }

    @ParameterizedTest
    @MethodSource("params")
    void testEncodeWithOffsetAndZeroLength(final ContentCodec codec) {
        assertThrows(CodecEncodingException.class, () -> testEncode(codec, DEFAULT_ALLOCATOR, 0));
    }

    private void testEncode(final ContentCodec codec, final BufferAllocator allocator) {
        testEncode(codec, allocator, INPUT.length());
    }

    private void testEncode(final ContentCodec codec, final BufferAllocator allocator, final int length) {
        Buffer source = allocator.fromAscii(INPUT);
        Buffer encoded = codec.encode(source.readSlice(length), DEFAULT_ALLOCATOR);

        assertThat(encoded, notNullValue());

        Buffer decoded = codec.decode(encoded, DEFAULT_ALLOCATOR);
        assertThat(decoded.toString(US_ASCII), equalTo(INPUT.substring(0, length)));
    }

    @ParameterizedTest
    @MethodSource("params")
    void testEncodeOverTheLimit(final ContentCodec codec) {
        byte[] input = new byte[(4 << 20) + 1]; //4MiB + 1 byte
        Arrays.fill(input, (byte) 'a');

        Buffer source = DEFAULT_ALLOCATOR.wrap(input);
        Buffer encoded = codec.encode(source, DEFAULT_ALLOCATOR);

        assertThrows(CodecDecodingException.class, () -> codec.decode(encoded, DEFAULT_ALLOCATOR));
    }

    @ParameterizedTest
    @MethodSource("params")
    void testEncodeOverTheLimitStreaming(final ContentCodec codec)
            throws ExecutionException, InterruptedException {
        byte[] input = new byte[(4 << 20) + 1]; //4MiB + 1 byte
        Arrays.fill(input, (byte) 'a');

        Buffer source = DEFAULT_ALLOCATOR.wrap(input);
        Buffer encoded = codec.encode(source, DEFAULT_ALLOCATOR);

        final AtomicReference<Exception> error = new AtomicReference<>();
        codec.decode(Publisher.from(EMPTY_BUFFER, encoded), DEFAULT_ALLOCATOR)
                .onErrorComplete(t -> {
                    error.set((Exception) t);
                    return true;
                }).toFuture().get();

        assertThrows(CodecDecodingException.class, () -> {
            throw error.get();
        });
    }

    @Test
    void testGzipIntegrationWithJDK() throws Exception {
        ContentCodec codec = ContentCodings.gzipDefault();

        // Deflate with JDK and inflate with ST
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos);
        gzipOutputStream.write(INPUT.getBytes(US_ASCII));
        gzipOutputStream.close();

        Buffer decoded = codec.decode(DEFAULT_ALLOCATOR.wrap(baos.toByteArray()), DEFAULT_ALLOCATOR);
        assertThat(decoded.toString(US_ASCII), equalTo(INPUT));

        // Deflate with ST and inflate with JDK
        Buffer source = DEFAULT_ALLOCATOR.fromAscii(INPUT);
        Buffer encoded = codec.encode(source.duplicate(), DEFAULT_ALLOCATOR);

        byte[] inflated = new byte[1024 * 2];
        ByteArrayInputStream bais = new ByteArrayInputStream(encoded.array(),
                encoded.arrayOffset() + encoded.readerIndex(), encoded.readableBytes());
        GZIPInputStream gzipInputStream = new GZIPInputStream(bais);
        int read = gzipInputStream.read(inflated);
        gzipInputStream.close();

        assertThat(new String(inflated, 0, read, US_ASCII), equalTo(INPUT));
    }

    @ParameterizedTest
    @MethodSource("params")
    void testEncodePublisher(final ContentCodec codec) throws ExecutionException, InterruptedException {
        Buffer source = DEFAULT_ALLOCATOR.fromAscii(INPUT);
        Buffer encoded = codec.encode(Publisher.from(source), DEFAULT_ALLOCATOR)
                .collect(DEFAULT_ALLOCATOR::newCompositeBuffer, CompositeBuffer::addBuffer).toFuture().get();
        // All codecs produce deflated buffers of less than 100 bytes
        assertThat(encoded.readableBytes(), lessThan(100));

        Buffer decoded = codec.decode(Publisher.from(encoded), DEFAULT_ALLOCATOR).firstOrError().toFuture().get();
        assertThat(decoded.toString(US_ASCII), equalTo(INPUT));
    }
}
