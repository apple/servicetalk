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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.encoding.api.CodecDecodingException;
import io.servicetalk.encoding.api.CodecEncodingException;
import io.servicetalk.encoding.api.ContentCodec;

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
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferUtils.extractByteBufOrCreate;
import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.buffer.netty.BufferUtils.newBufferFrom;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static java.util.Objects.requireNonNull;

@Deprecated
final class NettyChannelContentCodec extends AbstractContentCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyChannelContentCodec.class);
    private static final Buffer END_OF_STREAM = DEFAULT_RO_ALLOCATOR.fromAscii(" ");
    private static final int MAX_SIZE_FOR_MERGED_BUFFER = 1024; //1KiB

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
    public Buffer encode(final Buffer src, final BufferAllocator allocator) {
        requireNonNull(allocator);

        if (src.readableBytes() == 0) {
            throw new CodecEncodingException(this, "No data to encode.");
        }

        final MessageToByteEncoder<ByteBuf> encoder = encoderSupplier.get();
        final EmbeddedChannel channel = newEmbeddedChannel(encoder, allocator);

        try {
            ByteBuf origin = extractByteBufOrCreate(src);
            channel.writeOutbound(origin);

            // May produce footer
            preparePendingData(channel);

            final Buffer buffer = drainChannelQueueToSingleBuffer(channel.outboundMessages(), allocator);
            if (buffer == null) {
                throw new CodecEncodingException(this, "Not enough data to produce an encoded output");
            }

            cleanup(channel);
            return buffer;
        } catch (CodecEncodingException e) {
            throw e;
        } catch (Throwable e) {
            throw wrapEncodingException(this, e);
        } finally {
            safeCleanup(channel);
        }
    }

    @Override
    public Publisher<Buffer> encode(final Publisher<Buffer> from, final BufferAllocator allocator) {
        requireNonNull(from);
        requireNonNull(allocator);
        return from
                .concat(succeeded(END_OF_STREAM))
                .liftSync(subscriber -> new PublisherSource.Subscriber<Buffer>() {

                    private final MessageToByteEncoder<ByteBuf> encoder = encoderSupplier.get();
                    private final EmbeddedChannel channel = newEmbeddedChannel(encoder, allocator);

                    @Nullable
                    PublisherSource.Subscription subscription;

                    @Override
                    public void onSubscribe(PublisherSource.Subscription subscription) {
                        this.subscription = subscription;
                        subscriber.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(Buffer next) {
                        assert subscription != null;
                        if (!channel.isOpen()) {
                            throw new IllegalStateException("Stream encoder previously closed but more input arrived");
                        }

                        if (next == null) {
                            throw new CodecEncodingException(NettyChannelContentCodec.this,
                                    "Cannot encode null values");
                        }

                        try {
                            // onNext will produce AT-MOST N items (from upstream)
                            // +1 for the encoding footer (ie. END_OF_STREAM)
                            if (next == END_OF_STREAM) {
                                // May produce footer
                                preparePendingData(channel);
                                Buffer buffer = drainChannelQueueToSingleBuffer(channel.outboundMessages(), allocator);
                                if (buffer != null) {
                                    subscriber.onNext(buffer);
                                }

                                return;
                            }

                            channel.writeOutbound(extractByteBufOrCreate(next));
                            Buffer buffer = drainChannelQueueToSingleBuffer(channel.outboundMessages(), allocator);
                            if (buffer != null && buffer.readableBytes() > 0) {
                                subscriber.onNext(buffer);
                            } else {
                                subscription.request(1);
                            }
                        } catch (Throwable t) {
                            throw wrapEncodingException(NettyChannelContentCodec.this, t);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        safeCleanup(channel);
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        try {
                            cleanup(channel);
                        } catch (Throwable t) {
                            subscriber.onError(wrapEncodingException(NettyChannelContentCodec.this, t));
                            return;
                        }

                        subscriber.onComplete();
                    }
                });
    }

    @Override
    public Buffer decode(final Buffer src, final BufferAllocator allocator) {
        requireNonNull(allocator);

        if (src.readableBytes() == 0) {
            throw new CodecEncodingException(this, "No data to encode.");
        }

        final ByteToMessageDecoder decoder = decoderSupplier.get();
        final EmbeddedChannel channel = newEmbeddedChannel(decoder, allocator);

        try {
            ByteBuf origin = extractByteBufOrCreate(src);
            channel.writeInbound(origin);

            Buffer buffer = drainChannelQueueToSingleBuffer(channel.inboundMessages(), allocator);
            if (buffer == null) {
                throw new CodecDecodingException(this, "Not enough data to decode.");
            }

            cleanup(channel);
            return buffer;
        } catch (CodecDecodingException e) {
            throw e;
        } catch (Throwable e) {
            throw wrapDecodingException(this, e);
        } finally {
            safeCleanup(channel);
        }
    }

    @Override
    public Publisher<Buffer> decode(final Publisher<Buffer> from, final BufferAllocator allocator) {
        requireNonNull(from);
        requireNonNull(allocator);
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
                if (!channel.isOpen()) {
                    throw new CodecDecodingException(NettyChannelContentCodec.this,
                            "Stream decoder previously closed but more input arrived");
                }

                if (src == null) {
                    throw new CodecDecodingException(NettyChannelContentCodec.this, "Cannot decode null values");
                }

                try {
                    // onNext will produce AT-MOST N items (as received)
                    channel.writeInbound(extractByteBufOrCreate(src));
                    Buffer buffer = drainChannelQueueToSingleBuffer(channel.inboundMessages(), allocator);

                    if (buffer != null && buffer.readableBytes() > 0) {
                        subscriber.onNext(buffer);
                    } else {
                        // Not enough data to decompress, ask for more
                        subscription.request(1);
                    }
                } catch (Throwable e) {
                    throw wrapDecodingException(NettyChannelContentCodec.this, e);
                }
            }

            @Override
            public void onError(final Throwable t) {
                safeCleanup(channel);
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                try {
                    cleanup(channel);
                } catch (Throwable t) {
                    subscriber.onError(wrapDecodingException(NettyChannelContentCodec.this, t));
                    return;
                }

                subscriber.onComplete();
            }
        });
    }

    private EmbeddedChannel newEmbeddedChannel(final ChannelHandler handler, final BufferAllocator allocator) {
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.config().setAllocator(getByteBufAllocator(allocator));
        return channel;
    }

    private static void preparePendingData(final EmbeddedChannel channel) {
        try {
            channel.close().syncUninterruptibly().get();
            channel.checkException();
        } catch (InterruptedException | ExecutionException ex) {
            throwException(ex);
        }
    }

    @Nullable
    private static Buffer drainChannelQueueToSingleBuffer(
            final Queue<Object> queue, final BufferAllocator allocator) {
        if (queue.isEmpty()) {
            return null;
        }

        if (queue.size() == 1) {
            return newBufferFrom((ByteBuf) queue.poll());
        } else {
            int accumulateSize = 0;
            int components = 0;

            for (Object buffer : queue) {
                accumulateSize += ((ByteBuf) buffer).readableBytes();
                components++;
            }

            ByteBuf part;

            if (accumulateSize <= MAX_SIZE_FOR_MERGED_BUFFER) {
                // Try to merge everything together if total size is less than 1KiB (small chunks, ie. footer).
                // CompositeBuffer can require some additional work to write and may limit how much data we can
                // pass to writev (max of 1024 pointers), so if there are small chunks it may be better to combine them.
                Buffer merged = allocator.newBuffer();
                while ((part = (ByteBuf) queue.poll()) != null) {
                    merged.writeBytes(newBufferFrom(part));
                }

                return merged;
            }

            CompositeBuffer composite = allocator.newCompositeBuffer(components);
            while ((part = (ByteBuf) queue.poll()) != null) {
                composite.addBuffer(newBufferFrom(part));
            }

            return composite;
        }
    }

    private static void cleanup(final EmbeddedChannel channel) {
        boolean wasNotEmpty = channel.finishAndReleaseAll();
        assert !wasNotEmpty;
    }

    private static void safeCleanup(final EmbeddedChannel channel) {
        try {
            cleanup(channel);
        } catch (AssertionError error) {
          throw error;
        } catch (Throwable t) {
            LOGGER.error("Error while closing embedded channel", t);
        }
    }

    private static CodecEncodingException wrapEncodingException(final ContentCodec codec, final Throwable cause) {
        return new CodecEncodingException(codec, "Unexpected exception during encoding", cause);
    }

    private static CodecDecodingException wrapDecodingException(final ContentCodec codec, final Throwable cause) {
        return new CodecDecodingException(codec, "Unexpected exception during decoding", cause);
    }
}
