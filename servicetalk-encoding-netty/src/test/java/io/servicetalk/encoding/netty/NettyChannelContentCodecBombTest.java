/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.encoding.api.CodecDecodingException;
import io.servicetalk.encoding.api.ContentCodec;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Supplier;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors {@link NettyCompressionSerializerBombTest} for the deprecated
 * {@link ZipContentCodecBuilder} API which decodes via {@link NettyChannelContentCodec}.
 *
 * @deprecated Exercises the deprecated {@link io.servicetalk.encoding.api.ContentCodec} API;
 * kept until that API is removed alongside {@link ZipContentCodecBuilder}.
 */
@Deprecated
class NettyChannelContentCodecBombTest {

    private static final int TEST_MAX_CHUNK_SIZE = 128 << 20; // 128 MiB

    enum Codec {
        GZIP(NettyChannelContentCodecBombTest::gzipBomb, ContentCodings::gzip),
        DEFLATE(NettyChannelContentCodecBombTest::deflateBomb, ContentCodings::deflate);

        final BombFixture bomb;
        final Supplier<ZipContentCodecBuilder> builder;

        Codec(final BombFixture bomb, final Supplier<ZipContentCodecBuilder> builder) {
            this.bomb = bomb;
            this.builder = builder;
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void bombIsRejectedUnderCap(final Codec codec) throws IOException {
        final int payloadBytes = 64 << 20;
        final int capBytes = 16 << 20;
        final byte[] compressed = codec.bomb.build(payloadBytes);
        final ContentCodec contentCodec = codec.builder.get()
                .maxChunkSize(TEST_MAX_CHUNK_SIZE)
                .maxDecompressedBytes(capBytes)
                .build();
        final Buffer src = DEFAULT_ALLOCATOR.wrap(compressed);

        final CodecDecodingException ex = assertThrows(CodecDecodingException.class,
                () -> contentCodec.decode(src, DEFAULT_ALLOCATOR));
        assertThat(rootMessage(ex), containsString("exceeded"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void underCapRoundTrips(final Codec codec) throws IOException {
        final int payloadBytes = 10 << 20;
        final int capBytes = 16 << 20;
        final byte[] compressed = codec.bomb.build(payloadBytes);
        final ContentCodec contentCodec = codec.builder.get()
                .maxChunkSize(TEST_MAX_CHUNK_SIZE)
                .maxDecompressedBytes(capBytes)
                .build();

        final Buffer decoded = contentCodec.decode(DEFAULT_ALLOCATOR.wrap(compressed), DEFAULT_ALLOCATOR);
        assertThat(decoded.readableBytes(), equalTo(payloadBytes));
    }

    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void streamingIsUnboundedByDesign(final Codec codec) throws Exception {
        // The cap applies to the aggregated decode path only; streaming is intentionally left
        // uncapped. This test feeds a payload that would trip the aggregated cap through the
        // streaming API and asserts it round-trips.
        final int payloadBytes = 64 << 20;
        final int capBytes = 16 << 20;
        final byte[] compressed = codec.bomb.build(payloadBytes);
        final ContentCodec contentCodec = codec.builder.get()
                .maxChunkSize(TEST_MAX_CHUNK_SIZE)
                .maxDecompressedBytes(capBytes)
                .build();

        final CompositeBuffer result = contentCodec.decode(from(DEFAULT_ALLOCATOR.wrap(compressed)), DEFAULT_ALLOCATOR)
                .collect(DEFAULT_ALLOCATOR::newCompositeBuffer, CompositeBuffer::addBuffer)
                .toFuture().get();
        assertThat(result.readableBytes(), equalTo(payloadBytes));
    }

    private static String rootMessage(final Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : "";
    }

    @FunctionalInterface
    interface BombFixture {
        byte[] build(long decompressedBytes) throws IOException;
    }

    static byte[] gzipBomb(final long decompressedBytes) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            writeZeros(gz, decompressedBytes);
        }
        return out.toByteArray();
    }

    static byte[] deflateBomb(final long decompressedBytes) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream zlib = new DeflaterOutputStream(out)) {
            writeZeros(zlib, decompressedBytes);
        }
        return out.toByteArray();
    }

    private static void writeZeros(final OutputStream out, final long decompressedBytes) throws IOException {
        final byte[] chunk = new byte[64 * 1024];
        long remaining = decompressedBytes;
        while (remaining > 0) {
            final int n = (int) Math.min(chunk.length, remaining);
            out.write(chunk, 0, n);
            remaining -= n;
        }
    }
}
