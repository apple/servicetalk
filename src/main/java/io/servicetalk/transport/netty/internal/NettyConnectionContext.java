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

import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.IoExecutor;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static java.util.Objects.requireNonNull;

/**
 * {@link ConnectionContext} using a netty {@link Channel}.
 */
public final class NettyConnectionContext implements ConnectionContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyConnectionContext.class);

    private static final AttributeKey<ConnectionContext> SVC_CONTEXT = AttributeKey.newInstance(NettyConnectionContext.class.getName() + "_attr_service_context");

    private final IoExecutor ioExecutor;
    private final Executor executor;
    private final BufferAllocator allocator;
    private final NettyChannelListenableAsyncCloseable close;
    private final Channel channel;
    @Nullable
    private volatile SSLSession sslSession;

    /**
     * New instance.
     *
     * @param channel {@link Channel} for this connection.
     * @param ioExecutor {@link IoExecutor} for this connection.
     * @param executor {@link Executor} for this connection.
     * @param allocator {@link BufferAllocator} for this connection.
     */
    private NettyConnectionContext(Channel channel, IoExecutor ioExecutor, Executor executor,
                                   BufferAllocator allocator) {
        this.channel = requireNonNull(channel);
        close = new NettyChannelListenableAsyncCloseable(channel);
        this.ioExecutor = requireNonNull(ioExecutor);
        this.executor = requireNonNull(executor);
        this.allocator = requireNonNull(allocator);
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel.localAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public BufferAllocator getBufferAllocator() {
        return allocator;
    }

    @Override
    public SSLSession getSslSession() {
        return sslSession;
    }

    @Override
    public IoExecutor getIoExecutor() {
        return ioExecutor;
    }

    @Override
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Creates a new {@link NettyConnectionContext} by initializing the passed {@code channel} using the
     * {@code initializer}.
     *
     * @param channel for the newly created {@link NettyConnectionContext}.
     * @param ioExecutor the {@link IoExecutor} to use.
     * @param executor the {@link Executor} to use.
     * @param allocator for the context.
     * @param initializer to initialize the channel.
     * @return New {@link ConnectionContext} for the channel.
     */
    public static ConnectionContext newContext(Channel channel, IoExecutor ioExecutor, Executor executor,
                                               BufferAllocator allocator, ChannelInitializer initializer) {
        return newContext(channel, ioExecutor, executor, allocator, initializer, true);
    }

    /**
     * Creates a new {@link NettyConnectionContext} by initializing the passed {@code channel} using the
     * {@code initializer}.
     *
     * @param channel for the newly created {@link NettyConnectionContext}.
     * @param ioExecutor the {@link IoExecutor} to use.
     * @param executor the {@link Executor} to use.
     * @param allocator for the context.
     * @param initializer to initialize the channel.
     * @param checkForRefCountedTrapper Whether to log a warning if a {@link RefCountedTrapper} is not found in the pipeline.
     * @return New {@link ConnectionContext} for the channel.
     */
    public static ConnectionContext newContext(Channel channel, IoExecutor ioExecutor, Executor executor,
                                               BufferAllocator allocator, ChannelInitializer initializer,
                                               boolean checkForRefCountedTrapper) {
        ConnectionContext context = new NettyConnectionContext(channel, ioExecutor, executor, allocator);
        context = initializer.init(channel, context);
        if (checkForRefCountedTrapper) {
            RefCountedTrapper refCountedTrapper = channel.pipeline().get(RefCountedTrapper.class);
            if (refCountedTrapper == null) {
                LOGGER.warn("No handler of type {} found in the pipeline, this may leak ref-counted objects out of netty pipeline.",
                        RefCountedTrapper.class.getName());
            }
        }
        channel.attr(SVC_CONTEXT).set(context);
        return context;
    }

    /**
     * Retrieves the {@link ConnectionContext} associated with the passed {@code channel}.
     *
     * @param channel for which the context is to be retrieved.
     * @return {@link ConnectionContext} associated with the channel.
     *
     * @throws NoSuchElementException If no {@link ConnectionContext} was ever created for this {@code channel}.
     */
    public static ConnectionContext forChannel(Channel channel) {
        ConnectionContext ctx = channel.attr(SVC_CONTEXT).get();
        if (ctx == null) {
            throw new NoSuchElementException("No service context associated with this channel: " + channel);
        }
        return ctx;
    }

    void setSslSession(SSLSession session) {
        sslSession = session;
    }

    @Override
    public Completable onClose() {
        return close.onClose();
    }

    @Override
    public Completable closeAsync() {
        return close.closeAsync();
    }
}
