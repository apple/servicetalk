/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

import javax.annotation.Nullable;

import static io.netty.util.AttributeKey.newInstance;

/**
 * Utilities to handle {@link Channel} closure and its reason.
 */
public final class ChannelCloseUtils {

    private static final AttributeKey<Throwable> CONNECTION_ERROR = newInstance("ConnectionError");

    private ChannelCloseUtils() {
        // No instances
    }

    /**
     * Assigns a {@link Throwable} to the passed {@link Channel} to report it as a closure reason.
     *
     * @param channel a {@link Channel} to assign a {@link Throwable} to
     * @param error a {@link Throwable}
     */
    public static void assignConnectionError(final Channel channel, final Throwable error) {
        channel.attr(CONNECTION_ERROR).setIfAbsent(error);
    }

    /**
     * Close the passed {@link Channel} due to the observed {@link Throwable error}.
     *
     * @param channel a {@link Channel} to close
     * @param error a {@link Throwable error} that leads to the {@link Channel} closure
     * @return {@link ChannelFuture} that will be notified once the operation completes
     */
    public static ChannelFuture close(final Channel channel, final Throwable error) {
        channel.attr(CONNECTION_ERROR).setIfAbsent(error);
        return channel.close();
    }

    /**
     * Close the passed {@link ChannelHandlerContext} due to the observed {@link Throwable error}.
     *
     * @param ctx a {@link ChannelHandlerContext} to close
     * @param error a {@link Throwable error} that leads to the {@link ChannelHandlerContext} closure
     * @return {@link ChannelFuture} that will be notified once the operation completes
     */
    public static ChannelFuture close(final ChannelHandlerContext ctx, final Throwable error) {
        ctx.channel().attr(CONNECTION_ERROR).setIfAbsent(error);
        return ctx.close();
    }

    /**
     * Returns an {@link Throwable error} associated with the passed {@link Channel}.
     *
     * @param channel to look for a {@link Throwable}
     * @return an {@link Throwable error} associated with the passed {@link Channel}
     */
    @Nullable
    public static Throwable channelError(final Channel channel) {
        return channel.attr(CONNECTION_ERROR).getAndSet(null);
    }
}
