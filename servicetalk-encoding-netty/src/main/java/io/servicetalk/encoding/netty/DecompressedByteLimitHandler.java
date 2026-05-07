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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Caps the cumulative size of decoded chunks flowing inbound through the pipeline. The
 * decoder's own {@code maxChunkSize} bounds a single inflate step; this handler bounds the
 * total across all chunks within one channel lifetime, failing fast during decoding rather
 * than after the entire bomb has been inflated. Subclasses supply the codec-specific
 * {@link RuntimeException} to throw on overflow.
 */
abstract class DecompressedByteLimitHandler extends ChannelInboundHandlerAdapter {
    private final long maxBytes;
    private long total;

    DecompressedByteLimitHandler(final long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /**
     * Build the exception to throw when the cap is exceeded. Called once per overflow.
     *
     * @param maxBytes the configured cap, included in the exception message for diagnosis.
     * @return the exception that will propagate up the pipeline.
     */
    protected abstract RuntimeException newException(long maxBytes);

    @Override
    public final void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        if (msg instanceof ByteBuf) {
            final int n = ((ByteBuf) msg).readableBytes();
            // Overflow-safe form of (total + n > maxBytes); both maxBytes and total are
            // non-negative, and total <= maxBytes is the loop invariant.
            if (maxBytes - total < n) {
                ((ByteBuf) msg).release();
                throw newException(maxBytes);
            }
            total += n;
        }
        ctx.fireChannelRead(msg);
    }
}
