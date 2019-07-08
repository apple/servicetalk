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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.transport.api.ConnectionContext;

import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;

/**
 * Configures a {@link Channel}.
 */
public interface ChannelInitializer {

    /**
     * Configures the passed channel and optionally alters the passed {@link ConnectionContext}.
     * Typically, an initializer should add handlers to the channel at the end.
     * This makes it possible for the code using the initializer to create the order of the handlers in the pipeline.
     *
     * @param channel Netty channel.
     * @param context Service context for the channel.
     * @return Service context for the channel which may or may not be the same as the passed {@code context}.
     */
    ConnectionContext init(Channel channel, ConnectionContext context);

    /**
     * Returns a new {@link ChannelInitializer} which will first invoke this initializer and then {@code after}.
     *
     * @param after Initializer to call after this.
     * @return A new composite initializer.
     */
    default ChannelInitializer andThen(ChannelInitializer after) {
        return (channel, context) -> after.init(channel, init(channel, context));
    }

    /**
     * Default {@link ChannelInitializer}.
     *
     * @return Default initializer for ServiceTalk.
     */
    static ChannelInitializer defaultInitializer() {
        return (channel, context) -> {
            channel.config().setRecvByteBufAllocator(
                    new AdaptiveRecvByteBufAllocator(512, 32768, 65536)
                            .respectMaybeMoreData(false)
                            .maxMessagesPerRead(4));
            return context;
        };
    }
}
