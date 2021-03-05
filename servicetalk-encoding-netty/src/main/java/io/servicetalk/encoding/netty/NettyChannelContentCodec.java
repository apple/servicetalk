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
import io.servicetalk.buffer.netty.BufferUtils;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.netty.util.internal.PlatformDependent.throwException;
import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.buffer.netty.BufferUtils.newBufferFrom;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static java.util.Objects.requireNonNull;

final class NettyChannelContentCodec extends AbstractContentCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyChannelContentCodec.class);
    private static final Buffer END_OF_STREAM = DEFAULT_RO_ALLOCATOR.fromAscii(" ");

    private final Supplier<MessageToByteEncoder<ByteBuf>> encoderSupplier;
    private final Supplier<ByteToMessageDecoder> decoderSupplier;

    NettyChannelContentCodec(final CharSequence name,
                             final Supplier<MessageToByteEncoder<ByteBuf>> encoderSupplier,
                             final Supplier<ByteToMessageDecoder> decoderSupplier) {
        super(name);
        this.encoderSupplier = encoderSupplier;
        this.decoderSupplier = decoderSupplier;
    }

    @Override
    public Buffer encode(final Buffer src, final int offset, final int length, final BufferAllocator allocator) {
        requireNonNull(src);
        final int availableBytes = src.readableBytes() - offset;
        if (offset < 0) {
            throw new IllegalArgumentException("Invalid offset: " + offset + " (expected >= 0)");
        }

        if (length <= 0) {
            throw new IllegalArgumentException("Invalid length: " + length + " (expected > 0");
        }

        if (length > availableBytes) {
            throw new IllegalStateException("Invalid length: " + length + " (expected <= " + availableBytes);
        }

        final MessageToByteEncoder<ByteBuf> encoder = encoderSupplier.get();
        final EmbeddedChannel channel = newEmbeddedChannel(encoder, allocator);

        try {
            ByteBuf origin = toByteBuf(src, offset, length);
            channel.writeOutbound(origin);

            // May produce footer
            preparePendingData(channel);
            return requireNonNull(drainChannelOutToSingleBuffer(channel, allocator));
        } catch (Throwable e) {
            LOGGER.error("Error while encoding with {}", name(), e);
            safeCleanup(channel);
            throwException(e);
            return EMPTY_BUFFER;
        }
    }

    @Override
    public Publisher<Buffer> encode(final Publisher<Buffer> from, final BufferAllocator allocator) {
        requireNonNull(from);
        return from
                .concat(succeeded(END_OF_STREAM))
                .liftSync(subscriber -> new PublisherSource.Subscriber<Buffer>() {

                    private final MessageToByteEncoder<ByteBuf> encoder = encoderSupplier.get();
                    private final EmbeddedChannel channel = newEmbeddedChannel(encoder, allocator);

                    @Override
                    public void onSubscribe(PublisherSource.Subscription subscription) {
                        subscriber.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(Buffer next) {
                        if (next == null) {
                            return;
                        }

                        // onNext will produce AT-MOST N items (from upstream)
                        // +1 for the encoding footer (ie. END_OF_STREAM)
                        if (next == END_OF_STREAM) {
                            // May produce footer
                            preparePendingData(channel);
                            Buffer buffer = drainChannelOutToSingleBuffer(channel, allocator);
                            if (buffer != null) {
                                subscriber.onNext(buffer);
                            }

                            return;
                        }

                        channel.writeOutbound(toByteBuf(next));
                        Buffer buffer = drainChannelOutToSingleBuffer(channel, allocator);
                        if (buffer != null) {
                            subscriber.onNext(buffer);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        safeCleanup(channel);
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        cleanup(channel);
                        subscriber.onComplete();
                    }
                });
    }

    @Override
    public Buffer decode(final Buffer src, final int offset, final int length, final BufferAllocator allocator) {
        requireNonNull(src);
        final int availableBytes = src.readableBytes() - offset;
        if (offset < 0) {
            throw new IllegalArgumentException("Invalid offset: " + offset + " (expected >= 0)");
        }

        if (length <= 0) {
            throw new IllegalArgumentException("Invalid length: " + length + " (expected > 0)");
        }

        if (length > availableBytes) {
            throw new IllegalStateException("Invalid length: " + length + " (expected <= " + availableBytes);
        }

        final ByteToMessageDecoder decoder = decoderSupplier.get();
        final EmbeddedChannel channel = newEmbeddedChannel(decoder, allocator);

        try {
            ByteBuf origin = toByteBuf(src, offset, length);
            channel.writeInbound(origin);
            return newBufferFrom(channel.readInbound());
        } catch (Throwable e) {
            LOGGER.error("Error while decoding with {}", name(), e);
            throwException(e);
            return EMPTY_BUFFER;
        } finally {
            safeCleanup(channel);
        }
    }

    @Override
    public Publisher<Buffer> decode(final Publisher<Buffer> from, final BufferAllocator allocator) {
        requireNonNull(from);
        return from.liftSync(subscriber -> new PublisherSource.Subscriber<Buffer>() {

            private final ByteToMessageDecoder decoder = decoderSupplier.get();
            private final EmbeddedChannel channel = newEmbeddedChannel(decoder, allocator);

            @Nullable
            PublisherSource.Subscription subscription;

            @Override
            public void onSubscribe(final PublisherSource.Subscription subscription) {
                this.subscription = subscription;
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(@Nullable final Buffer src) {
                assert subscription != null;

                if (src == null) {
                    return;
                }

                // onNext will produce AT-MOST N items (as received)
                if (!channel.isOpen()) {
                    throw new IllegalStateException("Stream encoder previously closed but more input arrived ");
                }

                channel.writeInbound(toByteBuf(src));
                Buffer buffer = drainChannelInToSingleBuffer(channel, allocator);

                if (buffer != null && buffer.readableBytes() > 0) {
                    subscriber.onNext(buffer);
                } else {
                    // Not enough data to decompress, ask for more
                    subscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable t) {
                safeCleanup(channel);
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                cleanup(channel);
                subscriber.onComplete();
            }
        });
    }

    private EmbeddedChannel newEmbeddedChannel(final ChannelHandler handler, final BufferAllocator allocator) {
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.config().setAllocator(getByteBufAllocator(allocator));
        return channel;
    }

    private static ByteBuf toByteBuf(final Buffer buffer) {
        return BufferUtils.toByteBuf(buffer);
    }

    private static ByteBuf toByteBuf(final Buffer buffer, final int offset, final int length) {
        return BufferUtils.toByteBuf(buffer.readerIndex(buffer.readerIndex() + offset).readSlice(length));
    }

    private static void preparePendingData(final EmbeddedChannel channel) {
        try {
            channel.close().syncUninterruptibly().get();
            channel.checkException();
        } catch (InterruptedException | ExecutionException ex) {
            LOGGER.error("Unexpected exception from EmbeddedChannel", ex);
        }
    }

    @Nullable
    private static Buffer drainChannelInToSingleBuffer(
            final EmbeddedChannel channel, final BufferAllocator allocator) {
        if (channel.inboundMessages().isEmpty()) {
            return null;
        }

        if (channel.inboundMessages().size() == 1) {
            return newBufferFrom(channel.readInbound());
        } else {
            CompositeBuffer compositeBuffer = allocator.newCompositeBuffer();
            ByteBuf part;
            while ((part = channel.readInbound()) != null) {
                compositeBuffer.addBuffer(newBufferFrom(part));
            }

            return compositeBuffer;
        }
    }

    @Nullable
    private static Buffer drainChannelOutToSingleBuffer(
            final EmbeddedChannel channel, final BufferAllocator allocator) {
        if (channel.outboundMessages().isEmpty()) {
            return null;
        }

        if (channel.outboundMessages().size() == 1) {
            return newBufferFrom(channel.readOutbound());
        } else {
            int accumulateSize = 0;

            List<Buffer> parts = new ArrayList<>(channel.outboundMessages().size());
            ByteBuf part;
            while ((part = channel.readOutbound()) != null) {
                parts.add(newBufferFrom(part));
                accumulateSize += part.readableBytes();
            }

            // Try to merge everything together if total size is less than 1KiB (small chunks, ie. footer).
            // CompositeBuffer can requires some additional work to write and may limit how much data we can
            // pass to writev (max of 1024 pointers), so if there are small chunks it can be better to combine them.
            if (accumulateSize <= 1 << 10) {
                Buffer merged = allocator.newBuffer();
                for (Buffer b : parts) {
                    merged.writeBytes(b);
                }

                return merged;
            }

            CompositeBuffer composite = allocator.newCompositeBuffer(parts.size());
            for (Buffer b : parts) {
                composite.addBuffer(b);
            }

            return composite;
        }
    }

    private static void cleanup(final EmbeddedChannel channel) {
        boolean wasNotEmpty = channel.finishAndReleaseAll();
        channel.checkException();
        assert !wasNotEmpty;
    }

    private static void safeCleanup(final EmbeddedChannel channel) {
        try {
            cleanup(channel);
        } catch (Throwable t) {
            LOGGER.error("Error while closing embedded channel", t);
        }
    }
}
