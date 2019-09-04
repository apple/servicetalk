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

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.ApplicationProtocolNegotiator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;

import static io.servicetalk.transport.netty.internal.PooledRecvByteBufAllocatorInitializers.POOLED_ALLOCATOR;
import static java.util.Objects.requireNonNull;

final class WrappingSslContext extends SslContext {

    private final SslContext ctx;
    @Nullable
    private final String[] protocols;

    WrappingSslContext(SslContext ctx, @Nullable List<String> protocols) {
        this.ctx = requireNonNull(ctx);
        this.protocols = protocols == null ? null : protocols.toArray(new String[0]);
    }

    @Override
    public boolean isClient() {
        return ctx.isClient();
    }

    @Override
    public List<String> cipherSuites() {
        return ctx.cipherSuites();
    }

    @Override
    public long sessionCacheSize() {
        return ctx.sessionCacheSize();
    }

    @Override
    public long sessionTimeout() {
        return ctx.sessionTimeout();
    }

    @Override
    public ApplicationProtocolNegotiator applicationProtocolNegotiator() {
        return ctx.applicationProtocolNegotiator();
    }

    @Override
    public SSLEngine newEngine(ByteBufAllocator alloc) {
        SSLEngine engine = ctx.newEngine(alloc);
        initEngine(engine);
        return engine;
    }

    @Override
    public SSLEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort) {
        SSLEngine engine = ctx.newEngine(alloc, peerHost, peerPort);
        initEngine(engine);
        return engine;
    }

    private void initEngine(SSLEngine engine) {
        if (protocols != null) {
            engine.setEnabledProtocols(protocols);
        }
    }

    @Override
    public SSLSessionContext sessionContext() {
        return ctx.sessionContext();
    }

    @Override
    protected SslHandler newHandler(ByteBufAllocator alloc, boolean startTls) {
        return new SslHandlerWithPooledAllocator(newEngine(alloc), startTls);
    }

    @Override
    protected SslHandler newHandler(ByteBufAllocator alloc, boolean startTls, Executor executor) {
        return new SslHandlerWithPooledAllocator(newEngine(alloc), startTls, executor);
    }

    @Override
    protected SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort, boolean startTls) {
        return new SslHandlerWithPooledAllocator(newEngine(alloc, peerHost, peerPort), startTls);
    }

    @Override
    protected SslHandler newHandler(ByteBufAllocator alloc, String peerHost, int peerPort, boolean startTls,
                                    Executor executor) {
        return new SslHandlerWithPooledAllocator(newEngine(alloc, peerHost, peerPort), startTls, executor);
    }

    /**
     * {@link SslHandler} that overrides {@link ChannelHandlerContext#alloc()} to use {@link PooledByteBufAllocator}.
     */
    private static final class SslHandlerWithPooledAllocator extends SslHandler {

        @Nullable
        private ChannelHandlerContext wrappedCtx;

        SslHandlerWithPooledAllocator(SSLEngine engine, boolean startTls) {
            super(engine, startTls);
        }

        SslHandlerWithPooledAllocator(SSLEngine engine, boolean startTls, Executor delegatedTaskExecutor) {
            super(engine, startTls, delegatedTaskExecutor);
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            wrappedCtx = new DelegatingChannelHandlerContextWithPooledAllocator(ctx);
            super.handlerAdded(wrappedCtx);
        }

        @Override
        public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            super.disconnect(wrappedCtx(), promise);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            super.close(wrappedCtx(), promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            super.flush(wrappedCtx());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            super.channelRead(wrappedCtx(), msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(wrappedCtx());
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            super.userEventTriggered(wrappedCtx(), evt);
        }

        private ChannelHandlerContext wrappedCtx() {
            assert wrappedCtx != null;
            return wrappedCtx;
        }
    }

    /**
     * {@link ChannelHandlerContext} that delegates all calls to the original {@link ChannelHandlerContext}, but
     * returns {@link PooledByteBufAllocator} when {@link ChannelHandlerContext#alloc()} is invoked.
     */
    private static final class DelegatingChannelHandlerContextWithPooledAllocator implements ChannelHandlerContext {

        private ChannelHandlerContext ctx;

        DelegatingChannelHandlerContextWithPooledAllocator(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public ByteBufAllocator alloc() {
            return POOLED_ALLOCATOR;
        }

        @Override
        public Channel channel() {
            return ctx.channel();
        }

        @Override
        public EventExecutor executor() {
            return ctx.executor();
        }

        @Override
        public String name() {
            return ctx.name();
        }

        @Override
        public ChannelHandler handler() {
            return ctx.handler();
        }

        @Override
        public boolean isRemoved() {
            return ctx.isRemoved();
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            ctx.fireChannelRegistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            ctx.fireChannelUnregistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            ctx.fireChannelActive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            ctx.fireChannelInactive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            ctx.fireExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object evt) {
            ctx.fireUserEventTriggered(evt);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            ctx.fireChannelRead(msg);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            ctx.fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            ctx.fireChannelWritabilityChanged();
            return this;
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress) {
            return ctx.bind(localAddress);
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
            return ctx.bind(localAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress) {
            return ctx.connect(remoteAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return ctx.connect(remoteAddress, localAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            return ctx.connect(remoteAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            return ctx.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public ChannelFuture disconnect() {
            return ctx.disconnect();
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            return ctx.disconnect(promise);
        }

        @Override
        public ChannelFuture close() {
            return ctx.close();
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            return ctx.close(promise);
        }

        @Override
        public ChannelFuture deregister() {
            return ctx.deregister();
        }

        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            return ctx.deregister(promise);
        }

        @Override
        public ChannelHandlerContext read() {
            ctx.read();
            return this;
        }

        @Override
        public ChannelFuture write(Object msg) {
            return ctx.write(msg);
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return ctx.write(msg, promise);
        }

        @Override
        public ChannelHandlerContext flush() {
            ctx.flush();
            return this;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            return ctx.writeAndFlush(msg);
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            return ctx.writeAndFlush(msg, promise);
        }

        @Override
        public ChannelPromise newPromise() {
            return ctx.newPromise();
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return ctx.newProgressivePromise();
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return ctx.newSucceededFuture();
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            return ctx.newFailedFuture(cause);
        }

        @Override
        public ChannelPromise voidPromise() {
            return ctx.voidPromise();
        }

        @Override
        public ChannelPipeline pipeline() {
            return ctx.pipeline();
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return ctx.attr(key);
        }

        @Override
        public <T> boolean hasAttr(AttributeKey<T> key) {
            return ctx.hasAttr(key);
        }
    }
}
