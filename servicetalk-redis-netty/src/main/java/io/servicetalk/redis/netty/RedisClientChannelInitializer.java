/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.redis.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.RefCountedTrapper;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;

final class RedisClientChannelInitializer implements ChannelInitializer {
    // It doesn't really matter which allocator we pass in as we never use it.
    private static final RefCountedTrapper TRAPPER = new RefCountedTrapper(DEFAULT_ALLOCATOR) {
        @Override
        protected Object decode(EventLoop eventLoop, BufferAllocator allocator, Object msg) {
            return msg;
        }

        @Override
        public boolean isSharable() {
            return true;
        }
    };

    @Override
    public ConnectionContext init(final Channel channel, final ConnectionContext ctx) {
        final ChannelPipeline pipeline = channel.pipeline();
        // We only use the decoder, as encoding is trivial
        pipeline.addLast(new RedisDecoder());
        // Add the RefCountTrapper to ensure we always releaseAsync reference counted objects and also silence a warning.
        pipeline.addLast(TRAPPER);
        return ctx;
    }
}
