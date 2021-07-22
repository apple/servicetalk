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
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.encoding.api.BufferEncodingException;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.Queue;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferUtils.extractByteBufOrCreate;
import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.buffer.netty.BufferUtils.newBufferFrom;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.netty.NettyCompressionSerializer.cleanup;
import static io.servicetalk.encoding.netty.NettyCompressionSerializer.preparePendingData;
import static io.servicetalk.encoding.netty.NettyCompressionSerializer.safeCleanup;
import static java.util.Objects.requireNonNull;

final class NettyCompressionStreamingSerializer implements StreamingSerializerDeserializer<Buffer> {
    private static final Buffer END_OF_STREAM = DEFAULT_RO_ALLOCATOR.fromAscii(" ");
    private static final int MAX_SIZE_FOR_MERGED_BUFFER = 1 << 16;
    private final Supplier<MessageToByteEncoder<ByteBuf>> encoderSupplier;
    private final Supplier<ByteToMessageDecoder> decoderSupplier;

    NettyCompressionStreamingSerializer(final Supplier<MessageToByteEncoder<ByteBuf>> encoderSupplier,
                                        final Supplier<ByteToMessageDecoder> decoderSupplier) {
        this.encoderSupplier = requireNonNull(encoderSupplier);
        this.decoderSupplier = requireNonNull(decoderSupplier);
    }

    @Override
    public Publisher<Buffer> deserialize(final Publisher<Buffer> serializedData, final BufferAllocator allocator) {
        return serializedData.liftSync(subscriber -> new Subscriber<Buffer>() {
            private final ByteToMessageDecoder decoder = decoderSupplier.get();
            private final EmbeddedChannel channel = newEmbeddedChannel(decoder, allocator);
            @Nullable
            private Subscription subscription;

            @Override
            public void onSubscribe(final Subscription subscription) {
                this.subscription = ConcurrentSubscription.wrap(subscription);
                subscriber.onSubscribe(this.subscription);
            }

            @Override
            public void onNext(@Nullable final Buffer next) {
                assert subscription != null;
                if (next == null) {
                    subscriber.onNext(null);
                    return;
                }

                try { // onNext will produce AT-MOST N items (as received)
                    channel.writeInbound(extractByteBufOrCreate(next));
                    next.skipBytes(next.readableBytes());
                    Buffer buffer = drainChannelQueueToSingleBuffer(channel.inboundMessages(), allocator);
                    if (buffer != null && buffer.readableBytes() > 0) {
                        subscriber.onNext(buffer);
                    } else { // Not enough data to decompress, ask for more
                        subscription.request(1);
                    }
                } catch (Throwable t) {
                    throw new BufferEncodingException("Unexpected exception during decoding", t);
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
                    subscriber.onError(new BufferEncodingException("Unexpected exception during decoding", t));
                    return;
                }

                subscriber.onComplete();
            }
        });
    }

    @Override
    public Publisher<Buffer> serialize(final Publisher<Buffer> toSerialize, final BufferAllocator allocator) {
        return toSerialize
                .concat(succeeded(END_OF_STREAM))
                .liftSync(subscriber -> new Subscriber<Buffer>() {
                    private final MessageToByteEncoder<ByteBuf> encoder = encoderSupplier.get();
                    private final EmbeddedChannel channel = newEmbeddedChannel(encoder, allocator);
                    @Nullable
                    private Subscription subscription;

                    @Override
                    public void onSubscribe(Subscription subscription) {
                        this.subscription = ConcurrentSubscription.wrap(subscription);
                        subscriber.onSubscribe(this.subscription);
                    }

                    @Override
                    public void onNext(@Nullable Buffer next) {
                        assert subscription != null;
                        if (next == null) {
                            subscriber.onNext(null);
                            return;
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
                            } else {
                                channel.writeOutbound(extractByteBufOrCreate(next));
                                next.skipBytes(next.readableBytes());
                                Buffer buffer = drainChannelQueueToSingleBuffer(channel.outboundMessages(), allocator);
                                if (buffer != null && buffer.readableBytes() > 0) {
                                    subscriber.onNext(buffer);
                                } else {
                                    subscription.request(1);
                                }
                            }
                        } catch (Throwable t) {
                            throw new BufferEncodingException("Unexpected exception during encoding", t);
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
                            subscriber.onError(new BufferEncodingException("Unexpected exception during encoding", t));
                            return;
                        }

                        subscriber.onComplete();
                    }
                });
    }

    private static EmbeddedChannel newEmbeddedChannel(final ChannelHandler handler, final BufferAllocator allocator) {
        final EmbeddedChannel channel = new EmbeddedChannel(handler);
        channel.config().setAllocator(getByteBufAllocator(allocator));
        return channel;
    }

    @Nullable
    private static Buffer drainChannelQueueToSingleBuffer(final Queue<Object> queue, final BufferAllocator allocator) {
        if (queue.isEmpty()) {
            return null;
        } else if (queue.size() == 1) {
            return newBufferFrom((ByteBuf) queue.poll());
        } else {
            int accumulateSize = 0;
            for (Object buffer : queue) {
                accumulateSize += ((ByteBuf) buffer).readableBytes();
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

            CompositeBuffer composite = allocator.newCompositeBuffer(Integer.MAX_VALUE);
            while ((part = (ByteBuf) queue.poll()) != null) {
                composite.addBuffer(newBufferFrom(part));
            }

            return composite;
        }
    }
}
