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

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.netty.util.internal.PlatformDependent.throwException;
import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferUtils.newBufferFrom;
import static io.servicetalk.buffer.netty.BufferUtils.toByteBuf;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static java.util.Objects.requireNonNull;

final class NettyChannelContentCodec extends AbstractContentCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyChannelContentCodec.class);
    private static final Buffer END_OF_STREAM = DEFAULT_RO_ALLOCATOR.fromAscii(" ");

    private final Supplier<MessageToByteEncoder<ByteBuf>> encoderFn;
    private final Supplier<ByteToMessageDecoder> decoderFn;

    NettyChannelContentCodec(final CharSequence name,
                             final Supplier<MessageToByteEncoder<ByteBuf>> encoderFn,
                             final Supplier<ByteToMessageDecoder> decoderFn) {
        super(name);
        this.encoderFn = encoderFn;
        this.decoderFn = decoderFn;
    }

    @Override
    public Buffer encode(final Buffer src, final int offset, final int length,
                         final BufferAllocator allocator) {
        requireNonNull(src);
        if (offset < 0) {
            throw new IllegalArgumentException("Invalid offset: " + offset + " (expected >= 0)");
        }

        if (length <= 0) {
            throw new IllegalArgumentException("Invalid length: " + length + " (expected > 0)");
        }

        final MessageToByteEncoder<ByteBuf> encoder = encoderFn.get();
        final EmbeddedChannel channel = new EmbeddedChannel(encoder);

        try {
            ByteBuf origin = toByteBufRetained(src, offset, length);
            channel.writeOutbound(origin);

            // May produce footer
            channel.close();

            CompositeBuffer compositeBuffer = allocator.newCompositeBuffer();
            ByteBuf chunk;
            while ((chunk = channel.readOutbound()) != null) {
                compositeBuffer.addBuffer(newBufferFromRetained(chunk));
            }

            return compositeBuffer;
        } catch (Exception e) {
            LOGGER.error("Error while encoding with {}", name(), e);
            throwException(e);
            return EMPTY_BUFFER;
        } finally {
            safeClose(channel);
        }
    }

    @Override
    public Publisher<Buffer> encode(final Publisher<Buffer> from,
                                          final BufferAllocator __) {
        requireNonNull(from);
        return from
                .concat(succeeded(END_OF_STREAM))
                .liftSync(subscriber -> new PublisherSource.Subscriber<Buffer>() {

                    private final MessageToByteEncoder<ByteBuf> encoder = encoderFn.get();
                    private final EmbeddedChannel channel = new EmbeddedChannel(encoder);

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
                            channel.close();
                            ByteBuf part = channel.readOutbound();
                            if (part != null) {
                                subscriber.onNext(newBufferFromRetained(part));
                            }

                            return;
                        }

                        channel.writeOutbound(toByteBufRetained(next));
                        ByteBuf part = channel.readOutbound();
                        if (part != null) {
                            subscriber.onNext(newBufferFromRetained(part));
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        safeClose(channel);
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        safeClose(channel);
                        subscriber.onComplete();
                    }
                });
    }

    @Override
    public Buffer decode(final Buffer src, final int offset, final int length, final BufferAllocator __) {
        requireNonNull(src);

        if (offset < 0) {
            throw new IllegalArgumentException("Invalid offset: " + offset + " (expected >= 0)");
        }

        if (length <= 0) {
            throw new IllegalArgumentException("Invalid length: " + length + " (expected > 0)");
        }

        final ByteToMessageDecoder decoder = decoderFn.get();
        final EmbeddedChannel channel = new EmbeddedChannel(decoder);

        try {
            ByteBuf origin = toByteBufRetained(src, offset, length);
            channel.writeInbound(origin);
            return newBufferFromRetained(channel.readInbound());
        } catch (Exception e) {
            LOGGER.error("Error while decoding with {}", name(), e);
            throwException(e);
            return EMPTY_BUFFER;
        } finally {
            safeClose(channel);
        }
    }

    @Override
    public Publisher<Buffer> decode(final Publisher<Buffer> from, final BufferAllocator __) {
        requireNonNull(from);
        return from.liftSync(subscriber -> new PublisherSource.Subscriber<Buffer>() {

            private final ByteToMessageDecoder decoder = decoderFn.get();
            private final EmbeddedChannel channel = new EmbeddedChannel(decoder);

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

                channel.writeInbound(toByteBufRetained(src));
                ByteBuf part = channel.readInbound();

                if (part != null && part.readableBytes() > 0) {
                    subscriber.onNext(newBufferFromRetained(part));
                } else {
                    // Not enough data to decompress, ask for more
                    subscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable t) {
                safeClose(channel);
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                safeClose(channel);
                subscriber.onComplete();
            }
        });
    }

    private static Buffer newBufferFromRetained(final ByteBuf buf) {
        return newBufferFrom(buf.readRetainedSlice(buf.readableBytes()));
    }

    private static ByteBuf toByteBufRetained(final Buffer buffer) {
        return toByteBuf(buffer).retain();
    }

    private static ByteBuf toByteBufRetained(final Buffer buffer, final int offset, final int length) {
        return toByteBuf(
                buffer.readerIndex(buffer.readerIndex() + offset)
                      .readSlice(length))
                      .retain();
    }

    private static void safeClose(final EmbeddedChannel channel) {
        try {
            channel.finish();
        } catch (Throwable t) {
            LOGGER.error("Error while closing embedded channel", t);
        }
    }
}
