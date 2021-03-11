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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.encoding.api.CodecDecodingException;
import io.servicetalk.encoding.api.ContentCodec;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
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

@RunWith(Parameterized.class)
public class NettyChannelContentCodecTest {

    private static final String INPUT;
    static {
        byte[] arr = new byte[1024];
        Arrays.fill(arr, (byte) 'a');
        INPUT = new String(arr, US_ASCII);
    }

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final ContentCodec codec;

    public NettyChannelContentCodecTest(final ContentCodec codec) {
        this.codec = codec;
    }

    @Parameterized.Parameters(name = "codec={0}")
    public static ContentCodec[] params() {
        return new ContentCodec[]{
                ContentCodings.gzipDefault(),
                ContentCodings.deflateDefault(),
        };
    }

    @Test
    public void testEncode() {
        testEncode(DEFAULT_ALLOCATOR, 0);
    }

    @Test
    public void testEncodeWithOffset() {
        testEncode(DEFAULT_ALLOCATOR, 10);
    }

    @Test(expected = CodecDecodingException.class)
    public void testEncodeWithOffsetAndZeroLength() {
        testEncode(DEFAULT_ALLOCATOR, 10, 0);
    }

    @Test
    public void testEncodeWithOffsetAndReadOnlyBuffer() {
        testEncode(DEFAULT_RO_ALLOCATOR, 10);
    }

    private void testEncode(final BufferAllocator allocator, final int offset) {
        testEncode(allocator, offset, INPUT.length() - offset);
    }

    private void testEncode(final BufferAllocator allocator, final int offset, final int length) {
        Buffer source = allocator.fromAscii(INPUT);
        Buffer encoded = codec.encode(source, offset, length, DEFAULT_ALLOCATOR);

        assertThat(encoded, notNullValue());

        Buffer decoded = codec.decode(encoded, DEFAULT_ALLOCATOR);
        assertThat(decoded.toString(US_ASCII), equalTo(INPUT.substring(offset)));
    }

    @Test(expected = CodecDecodingException.class)
    public void testEncodeOverTheLimit() {
        byte[] input = new byte[(4 << 20) + 1]; //4MiB + 1 byte
        Arrays.fill(input, (byte) 'a');

        Buffer source = DEFAULT_ALLOCATOR.wrap(input);
        Buffer encoded = codec.encode(source, DEFAULT_ALLOCATOR);

        codec.decode(encoded, DEFAULT_ALLOCATOR);
    }

    @Test(expected = CodecDecodingException.class)
    public void testEncodeOverTheLimitStreaming() throws Exception {
        byte[] input = new byte[(4 << 20) + 1]; //4MiB + 1 byte
        Arrays.fill(input, (byte) 'a');

        Buffer source = DEFAULT_ALLOCATOR.wrap(input);
        Buffer encoded = codec.encode(source, DEFAULT_ALLOCATOR);

        final AtomicReference<Exception> error = new AtomicReference<>();
        codec.decode(Publisher.from(EMPTY_BUFFER, encoded), DEFAULT_ALLOCATOR)
                .recoverWith(t -> {
                    error.set((Exception) t);
                    return Publisher.empty();
                }).toFuture().get();

        throw error.get();
    }

    @Test
    public void testGzipIntegrationWithJDK() throws Exception {
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

    @Test
    public void testEncodePublisher()
            throws ExecutionException, InterruptedException {
        Buffer source = DEFAULT_ALLOCATOR.fromAscii(INPUT);
        Buffer encoded = codec.encode(Publisher.from(source), DEFAULT_ALLOCATOR)
                .collect(DEFAULT_ALLOCATOR::newCompositeBuffer, CompositeBuffer::addBuffer).toFuture().get();
        // All codecs produce deflated buffers of less than 100 bytes
        assertThat(encoded.readableBytes(), lessThan(100));

        Buffer decoded = codec.decode(Publisher.from(encoded), DEFAULT_ALLOCATOR).firstOrError().toFuture().get();
        assertThat(decoded.toString(US_ASCII), equalTo(INPUT));
    }
}
