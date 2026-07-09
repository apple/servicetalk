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
import io.servicetalk.encoding.api.BufferEncodingException;
import io.servicetalk.serializer.api.SerializerDeserializer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static io.netty.util.internal.PlatformDependent.throwException;
import static io.servicetalk.buffer.netty.BufferUtils.extractByteBufOrCreate;
import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.buffer.netty.BufferUtils.toByteBuf;
import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static java.util.Objects.requireNonNull;

final class NettyCompressionSerializer implements SerializerDeserializer<Buffer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyCompressionSerializer.class);
    private final Supplier<MessageToByteEncoder<ByteBuf>> encoderSupplier;
    private final Supplier<ByteToMessageDecoder> decoderSupplier;
    private final long maxDecompressedBytes;

    NettyCompressionSerializer(final Supplier<MessageToByteEncoder<ByteBuf>> encoderSupplier,
                               final Supplier<ByteToMessageDecoder> decoderSupplier,
                               final long maxDecompressedBytes) {
        this.encoderSupplier = requireNonNull(encoderSupplier);
        this.decoderSupplier = requireNonNull(decoderSupplier);
        this.maxDecompressedBytes = ensureNonNegative(maxDecompressedBytes, "maxDecompressedBytes");
    }

    @Override
    public void serialize(final Buffer toSerialize, final BufferAllocator allocator, final Buffer buffer) {
        final ByteBuf nettyDst = toByteBuf(buffer);
        final MessageToByteEncoder<ByteBuf> encoder = encoderSupplier.get();
        final EmbeddedChannel channel = newEmbeddedChannel(encoder, allocator);
        try {
            writeAndUpdateIndex(channel, toSerialize, false);
            // May produce footer
            preparePendingData(channel);
            drainChannelQueueToSingleBuffer(channel.outboundMessages(), nettyDst);
            // no need to advance writerIndex -> NettyBuffer's writerIndex reflects the underlying ByteBuf value.
            cleanup(channel);
        } catch (Throwable e) {
            safeCleanup(channel);
            throw new BufferEncodingException("Unexpected exception during encoding", e);
        }
    }

    @Override
    public Buffer serialize(final Buffer toSerialize, final BufferAllocator allocator) {
        Buffer buffer = allocator.newBuffer(toSerialize.readableBytes());
        serialize(toSerialize, allocator, buffer);
        return buffer;
    }

    @Override
    public Buffer deserialize(final Buffer serializedData, final BufferAllocator allocator) {
        final Buffer buffer = allocator.newBuffer(serializedData.readableBytes());
        final ByteBuf nettyDst = toByteBuf(buffer);
        final ByteToMessageDecoder decoder = decoderSupplier.get();
        final EmbeddedChannel channel = newEmbeddedDecoderChannel(decoder, allocator, maxDecompressedBytes);
        try {
            writeAndUpdateIndex(channel, serializedData, true);
            // Surface cap-exceeded errors (or any other decoder errors) that the pipeline caught.
            channel.checkException();
            drainChannelQueueToSingleBuffer(channel.inboundMessages(), nettyDst);
            // no need to advance writerIndex -> NettyBuffer's writerIndex reflects the underlying ByteBuf value.
            cleanup(channel);
            return buffer;
        } catch (Throwable e) {
            // The cap handler may leave decoded chunks on the inbound queue when it throws mid-
            // stream; releaseOnError tolerates that state (unlike safeCleanup, which would surface
            // an AssertionError under -ea and mask the real cause).
            releaseOnError(channel);
            if (e instanceof BufferEncodingException) {
                throw (BufferEncodingException) e;
            }
            throw new BufferEncodingException("Unexpected exception during decoding", e);
        }
    }

    static void writeAndUpdateIndex(EmbeddedChannel channel, Buffer toSerialize, boolean inbound) {
        ByteBuf byteBuf = extractByteBufOrCreate(toSerialize);
        final int beforeReadableBytes = byteBuf.readableBytes();
        if (inbound) {
            channel.writeInbound(byteBuf);
        } else {
            channel.writeOutbound(byteBuf);
        }
        // extractByteBufOrCreate may have to copy if it isn't able to unwrap NettyBuffer and in this case we have to
        // manually advance the Buffer indexes to reflect what was consumed.
        if (byteBuf.readableBytes() != toSerialize.readableBytes()) {
            toSerialize.skipBytes(beforeReadableBytes - byteBuf.readableBytes());
        }
    }

    static void drainChannelQueueToSingleBuffer(final Queue<Object> queue, final ByteBuf nettyDst) {
        ByteBuf buf;
        while ((buf = (ByteBuf) queue.poll()) != null) {
            try {
                nettyDst.writeBytes(buf);
            } finally {
                buf.release();
            }
        }
    }

    private static EmbeddedChannel newEmbeddedChannel(final ChannelHandler handler, final BufferAllocator allocator) {
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.config().setAllocator(getByteBufAllocator(allocator));
        return channel;
    }

    private static EmbeddedChannel newEmbeddedDecoderChannel(final ByteToMessageDecoder decoder,
                                                             final BufferAllocator allocator,
                                                             final long maxDecompressedBytes) {
        final EmbeddedChannel channel = maxDecompressedBytes > 0 ?
                new EmbeddedChannel(decoder, newLimitHandler(maxDecompressedBytes)) :
                new EmbeddedChannel(decoder);
        channel.config().setAllocator(getByteBufAllocator(allocator));
        return channel;
    }

    static DecompressedByteLimitHandler newLimitHandler(final long maxDecompressedBytes) {
        return new DecompressedByteLimitHandler(maxDecompressedBytes) {
            @Override
            protected RuntimeException newException(final long maxBytes) {
                return new BufferEncodingException(
                        "Decompressed payload exceeded maxDecompressedBytes=" + maxBytes);
            }
        };
    }

    static void preparePendingData(final EmbeddedChannel channel) {
        try {
            channel.close().sync().get();
            channel.checkException();
        } catch (InterruptedException | ExecutionException ex) {
            throwException(ex);
        }
    }

    static void cleanup(final EmbeddedChannel channel) {
        boolean wasNotEmpty = channel.finishAndReleaseAll();
        assert !wasNotEmpty;
    }

    static void safeCleanup(final EmbeddedChannel channel) {
        try {
            cleanup(channel);
        } catch (AssertionError error) {
            throw error;
        } catch (Throwable t) {
            LOGGER.debug("Error while closing embedded channel", t);
        }
    }

    /**
     * Release channel queues on an error path. Unlike {@link #safeCleanup}, this tolerates a
     * non-empty inbound/outbound queue — which is the expected state after a mid-stream failure
     * from a pipeline handler.
     */
    static void releaseOnError(final EmbeddedChannel channel) {
        try {
            channel.finishAndReleaseAll();
        } catch (Throwable t) {
            LOGGER.debug("Error while closing embedded channel", t);
        }
    }
}
