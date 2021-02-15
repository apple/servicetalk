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
import io.servicetalk.buffer.api.CompositeBuffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.encoding.api.ContentCodec;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.buffer.api.CharSequences.unwrapBuffer;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

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
                ContentCodings.snappyDefault(),
        };
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testEncodingOfReadOnlyBuffer() {
        Buffer source = requireNonNull(unwrapBuffer(newAsciiString(INPUT)));
        codec.encode(source, DEFAULT_ALLOCATOR);
    }

    @Test
    public void testEncode() {
        Buffer source = DEFAULT_ALLOCATOR.fromAscii(INPUT);
        Buffer encoded = codec.encode(source, DEFAULT_ALLOCATOR);
        // All codecs produce deflated buffers of less than 100 bytes
        // ContentCodec{name=gzip} 25
        // ContentCodec{name=deflate} 17
        // ContentCodec{name=snappy} 70
        assertThat(encoded.readableBytes(), lessThan(100));
        Buffer decoded = codec.decode(encoded, DEFAULT_ALLOCATOR);
        assertThat(decoded.toString(US_ASCII), equalTo(INPUT));
    }

    @Test
    public void testEncode_checkAccessibility() {
        Buffer source = DEFAULT_ALLOCATOR.fromAscii(INPUT);
        Buffer encoded = codec.encode(source, DEFAULT_ALLOCATOR);

        // We own this, reset the index and try to decode again.
        // If the reference wasn't retained this could fail
        source.readerIndex(0);
        encoded = codec.encode(source, DEFAULT_ALLOCATOR);

        codec.decode(encoded, DEFAULT_ALLOCATOR);
        // We own this, reset the index and try to decode again.
        // If the reference wasn't retained this could fail
        encoded.readerIndex(0);
        codec.decode(encoded, DEFAULT_ALLOCATOR);
    }

    @Test
    public void testEncodePublisher()
            throws ExecutionException, InterruptedException {
        Buffer source = DEFAULT_ALLOCATOR.fromAscii(INPUT);
        Buffer encoded = codec.encode(Publisher.from(source), DEFAULT_ALLOCATOR)
                .collect(DEFAULT_ALLOCATOR::newCompositeBuffer, CompositeBuffer::addBuffer).toFuture().get();
        // All codecs produce deflated buffers of less than 100 bytes
        // ContentCodec{name=gzip} 25
        // ContentCodec{name=deflate} 17
        // ContentCodec{name=snappy} 70
        assertThat(encoded.readableBytes(), lessThan(100));

        Buffer decoded = codec.decode(Publisher.from(encoded), DEFAULT_ALLOCATOR).firstOrError().toFuture().get();
        assertThat(decoded.toString(US_ASCII), equalTo(INPUT));
    }
}
