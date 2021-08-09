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
import javax.annotation.Nullable;

import static io.netty.util.internal.PlatformDependent.throwException;
import static io.servicetalk.buffer.netty.BufferUtils.extractByteBufOrCreate;
import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.buffer.netty.BufferUtils.toByteBuf;
import static java.util.Objects.requireNonNull;

final class NettyCompressionSerializer implements SerializerDeserializer<Buffer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyCompressionSerializer.class);
    private final Supplier<MessageToByteEncoder<ByteBuf>> encoderSupplier;
    private final Supplier<ByteToMessageDecoder> decoderSupplier;

    NettyCompressionSerializer(final Supplier<MessageToByteEncoder<ByteBuf>> encoderSupplier,
                               final Supplier<ByteToMessageDecoder> decoderSupplier) {
        this.encoderSupplier = requireNonNull(encoderSupplier);
        this.decoderSupplier = requireNonNull(decoderSupplier);
    }

    @Override
    public void serialize(final Buffer toSerialize, final BufferAllocator allocator, final Buffer buffer) {
        final ByteBuf nettyDst = toByteBuf(buffer);
        final MessageToByteEncoder<ByteBuf> encoder = encoderSupplier.get();
        final EmbeddedChannel channel = newEmbeddedChannel(encoder, allocator);
        try {
            channel.writeOutbound(extractByteBufOrCreate(toSerialize));
            toSerialize.skipBytes(toSerialize.readableBytes());

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
        final EmbeddedChannel channel = newEmbeddedChannel(decoder, allocator);
        try {
            channel.writeInbound(toByteBuf(serializedData));
            serializedData.skipBytes(serializedData.readableBytes());

            drainChannelQueueToSingleBuffer(channel.inboundMessages(), nettyDst);
            // no need to advance writerIndex -> NettyBuffer's writerIndex reflects the underlying ByteBuf value.
            cleanup(channel);
            return buffer;
        } catch (Throwable e) {
            safeCleanup(channel);
            throw new BufferEncodingException("Unexpected exception during decoding", e);
        }
    }

    @Nullable
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

    static void preparePendingData(final EmbeddedChannel channel) {
        try {
            channel.close().syncUninterruptibly().get();
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
}
