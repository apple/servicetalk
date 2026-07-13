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
import io.servicetalk.encoding.api.BufferEncodingException;
import io.servicetalk.serializer.api.MaxMessageSizeExceededException;
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.encoding.netty.NettyCompression.deflate;
import static io.servicetalk.encoding.netty.NettyCompression.deflateDefault;
import static io.servicetalk.encoding.netty.NettyCompression.gzip;
import static io.servicetalk.encoding.netty.NettyCompression.gzipDefault;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the {@code maxDecompressedBytes} cap that prevents decompression-bomb DoS on both the
 * aggregated {@link SerializerDeserializer#deserialize} and streaming
 * {@link StreamingSerializerDeserializer#deserialize} paths.
 *
 * <p>Tests widen {@code maxChunkSize} so that the cumulative {@code maxDecompressedBytes} cap is
 * the binding limit exercised by each scenario — the default {@code maxChunkSize} (4 MiB) would
 * otherwise trip the decoder's own per-chunk allocation guard first for larger payloads.
 */
class NettyCompressionSerializerBombTest {

    // Large enough to let test payloads flow through per-chunk; the cap is what we're exercising.
    private static final int TEST_MAX_CHUNK_SIZE = 128 << 20; // 128 MiB

    private static Level originalLeakLevel;

    @BeforeAll
    static void enableLeakDetection() {
        originalLeakLevel = ResourceLeakDetector.getLevel();
        ResourceLeakDetector.setLevel(Level.PARANOID);
    }

    @AfterAll
    static void restoreLeakDetection() {
        ResourceLeakDetector.setLevel(originalLeakLevel);
    }

    enum Codec {
        GZIP(NettyCompressionSerializerBombTest::gzipBomb, NettyCompression::gzip),
        DEFLATE(NettyCompressionSerializerBombTest::deflateBomb, NettyCompression::deflate);

        final BombFixture bomb;
        final Supplier<ZipCompressionBuilder> builder;

        Codec(final BombFixture bomb, final Supplier<ZipCompressionBuilder> builder) {
            this.bomb = bomb;
            this.builder = builder;
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void bombIsRejectedUnderCap(final Codec codec) throws IOException {
        // 64 MiB decompressed from all-zeros input compresses to ~64 KiB — the canonical bomb
        // shape. Cap at 16 MiB to prove the cumulative limit rejects a payload far larger than
        // the cap. maxChunkSize is kept wide so we exercise our cap rather than the decoder's
        // own per-chunk allocation guard.
        final int payloadBytes = 64 << 20;
        final int capBytes = 16 << 20;
        final byte[] compressed = codec.bomb.build(payloadBytes);
        final SerializerDeserializer<Buffer> serializer = codec.builder.get()
                .maxChunkSize(TEST_MAX_CHUNK_SIZE)
                .maxDecompressedBytes(capBytes)
                .build();
        final Buffer src = DEFAULT_ALLOCATOR.wrap(compressed);

        final MaxMessageSizeExceededException ex = assertThrows(MaxMessageSizeExceededException.class,
                () -> serializer.deserialize(src, DEFAULT_ALLOCATOR));
        assertThat(rootMessage(ex), containsString("exceeded"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void underCapRoundTrips(final Codec codec) throws IOException {
        final int payloadBytes = 10 << 20; // 10 MiB
        final int capBytes = 16 << 20;     // 16 MiB
        final byte[] compressed = codec.bomb.build(payloadBytes);
        final SerializerDeserializer<Buffer> serializer = codec.builder.get()
                .maxChunkSize(TEST_MAX_CHUNK_SIZE)
                .maxDecompressedBytes(capBytes)
                .build();

        final Buffer decoded = serializer.deserialize(DEFAULT_ALLOCATOR.wrap(compressed), DEFAULT_ALLOCATOR);
        assertThat(decoded.readableBytes(), equalTo(payloadBytes));
    }

    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void zeroCapIsUnbounded(final Codec codec) throws IOException {
        final int payloadBytes = 8 << 20; // 8 MiB
        final byte[] compressed = codec.bomb.build(payloadBytes);
        final SerializerDeserializer<Buffer> serializer = codec.builder.get()
                .maxChunkSize(TEST_MAX_CHUNK_SIZE)
                // explicit 0 opts out of the default cap.
                .maxDecompressedBytes(0L)
                .build();

        final Buffer decoded = assertDoesNotThrow(() -> serializer.deserialize(
                DEFAULT_ALLOCATOR.wrap(compressed), DEFAULT_ALLOCATOR));
        assertThat(decoded.readableBytes(), equalTo(payloadBytes));
    }

    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void exactBoundarySucceeds(final Codec codec) throws IOException {
        final int capBytes = 1 << 20; // 1 MiB
        final byte[] compressed = codec.bomb.build(capBytes); // decompresses to exactly cap
        final SerializerDeserializer<Buffer> serializer = codec.builder.get()
                .maxChunkSize(TEST_MAX_CHUNK_SIZE)
                .maxDecompressedBytes(capBytes)
                .build();

        final Buffer decoded = serializer.deserialize(DEFAULT_ALLOCATOR.wrap(compressed), DEFAULT_ALLOCATOR);
        assertThat(decoded.readableBytes(), equalTo(capBytes));
    }

    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void oneByteOverBoundaryFails(final Codec codec) throws IOException {
        final int capBytes = 1 << 20; // 1 MiB
        final byte[] compressed = codec.bomb.build(capBytes + 1L);
        final SerializerDeserializer<Buffer> serializer = codec.builder.get()
                .maxChunkSize(TEST_MAX_CHUNK_SIZE)
                .maxDecompressedBytes(capBytes)
                .build();
        final Buffer src = DEFAULT_ALLOCATOR.wrap(compressed);

        final MaxMessageSizeExceededException ex = assertThrows(MaxMessageSizeExceededException.class,
                () -> serializer.deserialize(src, DEFAULT_ALLOCATOR));
        assertThat(rootMessage(ex), containsString("exceeded"));
    }

    /**
     * Concatenated gzip members force the decoder to emit one inbound chunk per member, which is
     * the only way to exercise the inline {@code DecompressedByteLimitHandler} path through a
     * single {@code deserialize} call (a single gzip stream produces a single growing output
     * buffer per decode call). Without the handler, a 6 MiB bomb would be fully inflated into the
     * channel queue before any cap check runs; with the handler, the pipeline fails fast after
     * the 2nd or 3rd member. Uses {@link NettyCompressionSerializer} directly because
     * {@link ZipCompressionBuilder} does not expose {@code decompressConcatenated}.
     */
    @Test
    void handlerRejectsMidStreamOnConcatenatedGzipMembers() throws IOException {
        final int memberSize = 1 << 20;   // 1 MiB per member
        final int memberCount = 6;        // 6 MiB total decompressed
        final int capBytes = 2 << 20;     // 2 MiB cap — should trip after the 2nd or 3rd member
        final int maxChunkSize = 128 << 20;
        final byte[] compressed = concatenatedGzipMembers(memberSize, memberCount);
        final SerializerDeserializer<Buffer> serializer = new NettyCompressionSerializer(
                () -> new JdkZlibEncoder(ZlibWrapper.GZIP, 6),
                () -> new JdkZlibDecoder(ZlibWrapper.GZIP, true, maxChunkSize),
                capBytes);
        final Buffer src = DEFAULT_ALLOCATOR.wrap(compressed);

        final MaxMessageSizeExceededException ex = assertThrows(MaxMessageSizeExceededException.class,
                () -> serializer.deserialize(src, DEFAULT_ALLOCATOR));
        assertThat(rootMessage(ex), containsString("exceeded"));
    }

    private static byte[] concatenatedGzipMembers(final int memberSize, final int memberCount) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < memberCount; i++) {
            try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
                writeZeros(gz, memberSize);
            }
        }
        return out.toByteArray();
    }

    /**
     * The convenience factories on {@link NettyCompression} must be safe-by-default — a service
     * that drops in {@code gzipDefault()} should be protected from decompression bombs without
     * any extra configuration. Either the per-chunk {@code maxChunkSize} guard inside
     * {@code JdkZlibDecoder} or the cumulative {@code maxDecompressedBytes} cap is acceptable;
     * the test only asserts that the bomb does <em>not</em> silently inflate.
     */
    @Test
    void gzipDefaultRejectsBombsLargerThanDefault() throws IOException {
        final long payloadBytes = 128L << 20;
        final byte[] compressed = gzipBomb(payloadBytes);
        assertThrows(BufferEncodingException.class,
                () -> gzipDefault().deserialize(DEFAULT_ALLOCATOR.wrap(compressed), DEFAULT_ALLOCATOR));
    }

    @Test
    void deflateDefaultRejectsBombsLargerThanDefault() throws IOException {
        final long payloadBytes = 128L << 20;
        final byte[] compressed = deflateBomb(payloadBytes);
        assertThrows(BufferEncodingException.class,
                () -> deflateDefault().deserialize(DEFAULT_ALLOCATOR.wrap(compressed), DEFAULT_ALLOCATOR));
    }

    /**
     * Streaming intentionally ignores {@code maxDecompressedBytes}. Build the streaming
     * serializer with a deliberately tiny cap and a wide {@code maxChunkSize}; the same payload
     * that the aggregated path would reject must round-trip through the streaming path.
     */
    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void streamingIgnoresCap(final Codec codec) throws Exception {
        final int payloadBytes = 8 << 20;     // 8 MiB
        final int capBytes = 1 << 20;         // 1 MiB — would reject on aggregated path
        final byte[] compressed = codec.bomb.build(payloadBytes);
        final StreamingSerializerDeserializer<Buffer> serializer = codec.builder.get()
                .maxChunkSize(TEST_MAX_CHUNK_SIZE)
                .maxDecompressedBytes(capBytes)
                .buildStreaming();

        final CompositeBuffer result = serializer.deserialize(
                        from(DEFAULT_ALLOCATOR.wrap(compressed)), DEFAULT_ALLOCATOR)
                .collect(DEFAULT_ALLOCATOR::newCompositeBuffer, CompositeBuffer::addBuffer)
                .toFuture().get();
        assertThat(result.readableBytes(), equalTo(payloadBytes));
    }

    /**
     * A single chunk that inflates past {@code maxChunkSize} fails fast (the decoder's own
     * per-inflate {@code maxAllocation} guard) rather than allocating unbounded memory.
     */
    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void streamingBoundedByMaxChunkSize(final Codec codec) throws IOException {
        final int maxChunkSize = 1 << 20;     // 1 MiB single-inflate guard
        final long payloadBytes = 64L << 20;  // 64 MiB from one member -> 64x the guard, must trip it
        final byte[] compressed = codec.bomb.build(payloadBytes);
        final StreamingSerializerDeserializer<Buffer> serializer = codec.builder.get()
                .maxChunkSize(maxChunkSize)
                .maxDecompressedBytes(0L)
                .buildStreaming();

        final ExecutionException ex = assertThrows(ExecutionException.class,
                () -> serializer.deserialize(from(DEFAULT_ALLOCATOR.wrap(compressed)), DEFAULT_ALLOCATOR)
                        .collect(DEFAULT_ALLOCATOR::newCompositeBuffer, CompositeBuffer::addBuffer)
                        .toFuture().get());
        assertThat(rootCause(ex), instanceOf(DecompressionException.class));
    }

    /**
     * A single chunk that inflates to less than {@code maxChunkSize} round-trips unchanged.
     */
    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void streamingUnderMaxChunkSizeRoundTrips(final Codec codec) throws Exception {
        final int maxChunkSize = 4 << 20;     // 4 MiB single-inflate guard
        final int payloadBytes = 1 << 20;     // 1 MiB, comfortably under the guard
        final byte[] compressed = codec.bomb.build(payloadBytes);
        final StreamingSerializerDeserializer<Buffer> serializer = codec.builder.get()
                .maxChunkSize(maxChunkSize)
                .maxDecompressedBytes(0L)
                .buildStreaming();

        final CompositeBuffer result = serializer.deserialize(
                        from(DEFAULT_ALLOCATOR.wrap(compressed)), DEFAULT_ALLOCATOR)
                .collect(DEFAULT_ALLOCATOR::newCompositeBuffer, CompositeBuffer::addBuffer)
                .toFuture().get();

        assertThat(result.readableBytes(), equalTo(payloadBytes));
        // our uncompressed bomb should be a sequence of 0's.
        assertEquals(-1, result.forEachByte((b) -> b == 0));
    }

    /**
     * Variant that targets the cumulative cap specifically: with a wide {@code maxChunkSize} the
     * 64 MiB default is the binding limit, and the resulting exception message must mention
     * "exceeded" to prove the cap (not the decoder's per-chunk guard) fired.
     */
    @ParameterizedTest(name = "{displayName} [{index}] codec = {0}")
    @EnumSource(Codec.class)
    void defaultBuilderCapEnforcesAt64MiB(final Codec codec) throws IOException {
        final long payloadBytes = 80L << 20; // 80 MiB > 64 MiB default
        final byte[] compressed = codec.bomb.build(payloadBytes);
        // Use the builder directly with widened maxChunkSize so the only binding limit is the
        // builder's maxDecompressedBytes default. The cap setter is intentionally NOT called.
        final SerializerDeserializer<Buffer> serializer = codec.builder.get()
                .maxChunkSize(TEST_MAX_CHUNK_SIZE)
                .build();

        final MaxMessageSizeExceededException ex = assertThrows(MaxMessageSizeExceededException.class,
                () -> serializer.deserialize(DEFAULT_ALLOCATOR.wrap(compressed), DEFAULT_ALLOCATOR));
        assertThat(rootMessage(ex), containsString("exceeded"));
    }

    private static String rootMessage(final Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : "";
    }

    private static Throwable rootCause(final Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
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
