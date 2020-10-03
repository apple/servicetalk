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
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.SocketChannelConfig;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;

/**
 * {@link EmbeddedChannel} that implements {@link DuplexChannel}.
 */
public final class EmbeddedDuplexChannel extends EmbeddedChannel implements DuplexChannel {

    // Use atomics because shutdown may be requested from offloaded thread,
    // while EmbeddedEventLoop#inEventLoop() always returns `true`.
    private final AtomicBoolean isInputShutdown = new AtomicBoolean();
    private final AtomicBoolean isOutputShutdown = new AtomicBoolean();

    private final CountDownLatch inputShutdownLatch = new CountDownLatch(1);
    private final CountDownLatch outputShutdownLatch = new CountDownLatch(1);

    @Nullable
    private EmbeddedDuplexChannelConfig config;
    @Nullable
    private EmbeddedUnsafe unsafe;

    /**
     * Create a new instance with the pipeline initialized with the specified handlers.
     *
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedDuplexChannel(ChannelHandler... handlers) {
        super(handlers);
    }

    @Override
    protected void doClose() throws Exception {
        super.doClose();
        isInputShutdown.set(true);
        doShutdownOutput();
    }

    @Override
    public boolean isInputShutdown() {
        return isInputShutdown.get();
    }

    /**
     * Awaits completion of {@link #shutdownInput()}.
     *
     * @throws InterruptedException if the current thread was interrupted
     */
    public void awaitInputShutdown() throws InterruptedException {
        inputShutdownLatch.await();
    }

    @Override
    public ChannelFuture shutdownInput() {
        return shutdownInput(newPromise());
    }

    @Override
    public ChannelFuture shutdownInput(final ChannelPromise promise) {
        if (!isOpen()) {
            promise.setFailure(new ClosedChannelException());
            return promise;
        }

        assert config != null;
        if (!config.isAllowHalfClosure()) {
            return close(promise);
        }

        if (isInputShutdown.compareAndSet(false, true)) {
            pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
            super.flushInbound();
            pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
            inputShutdownLatch.countDown();
        }
        promise.setSuccess();
        return promise;
    }

    @Override
    public boolean isOutputShutdown() {
        return isOutputShutdown.get();
    }

    /**
     * Awaits completion of {@link #shutdownOutput()}.
     *
     * @throws InterruptedException if the current thread was interrupted
     */
    public void awaitOutputShutdown() throws InterruptedException {
        outputShutdownLatch.await();
    }

    @Override
    public ChannelFuture shutdownOutput() {
        return shutdownOutput(newPromise());
    }

    @Override
    public ChannelFuture shutdownOutput(final ChannelPromise promise) {
        if (!isOpen()) {
            promise.setFailure(new ClosedChannelException());
            return promise;
        }

        assert config != null;
        if (!config.isAllowHalfClosure()) {
            return close(promise);
        }

        assert unsafe != null;
        unsafe.shutdownOutput(promise);
        return promise;
    }

    @Override
    protected void doShutdownOutput() {
        isOutputShutdown.set(true);
        outputShutdownLatch.countDown();
    }

    @Override
    public boolean isShutdown() {
        return isInputShutdown() && isOutputShutdown();
    }

    @Override
    public ChannelFuture shutdown() {
        return shutdown(newPromise());
    }

    @Override
    public ChannelFuture shutdown(final ChannelPromise promise) {
        ChannelFuture shutdownOutputFuture = shutdownOutput();
        if (shutdownOutputFuture.isDone()) {
            shutdownOutputDone(shutdownOutputFuture, promise);
        } else {
            shutdownOutputFuture.addListener((ChannelFutureListener) sof -> shutdownOutputDone(sof, promise));
        }
        return promise;
    }

    private void shutdownOutputDone(final ChannelFuture shutdownOutputFuture, final ChannelPromise promise) {
        ChannelFuture shutdownInputFuture = shutdownInput();
        if (shutdownInputFuture.isDone()) {
            shutdownDone(shutdownOutputFuture, shutdownInputFuture, promise);
        } else {
            shutdownInputFuture.addListener((ChannelFutureListener) sif ->
                    shutdownDone(shutdownOutputFuture, sif, promise));
        }
    }

    private static void shutdownDone(ChannelFuture shutdownOutputFuture,
                                     ChannelFuture shutdownInputFuture,
                                     ChannelPromise promise) {
        Throwable shutdownOutputCause = shutdownOutputFuture.cause();
        Throwable shutdownInputCause = shutdownInputFuture.cause();
        if (shutdownOutputCause != null) {
            if (shutdownInputCause != null) {
                shutdownOutputCause.addSuppressed(shutdownInputCause);
            }
            promise.setFailure(shutdownOutputCause);
        } else if (shutdownInputCause != null) {
            promise.setFailure(shutdownInputCause);
        } else {
            promise.setSuccess();
        }
    }

    @Override
    public Queue<Object> inboundMessages() {
        if (isInputShutdown.get()) {
            // Best effort to prevent external manipulations of internal inboundMessages queue:
            return new ArrayDeque<>(super.inboundMessages());
        }
        return super.inboundMessages();
    }

    @Override
    public ChannelFuture writeOneInbound(final Object msg, final ChannelPromise promise) {
        if (isInputShutdown.get()) {
            promise.setSuccess();   // Ignore new inbound
            return promise;
        }
        return super.writeOneInbound(msg, promise);
    }

    @Override
    public boolean writeInbound(final Object... msgs) {
        if (isInputShutdown.get()) {
            // Ignore new inbound
            return false;
        }
        return super.writeInbound(msgs);
    }

    @Override
    public EmbeddedChannel flushInbound() {
        if (isInputShutdown.get()) {
            // Ignore new inbound
            return this;
        }
        return super.flushInbound();
    }

    @Override
    protected void handleInboundMessage(final Object msg) {
        if (isInputShutdown.get()) {
            // Ignore new inbound
            return;
        }
        super.handleInboundMessage(msg);
    }

    @Override
    public Queue<Object> outboundMessages() {
        if (isOutputShutdown.get()) {
            // Best effort to prevent external manipulations of internal outboundMessages queue:
            return new ArrayDeque<>(super.outboundMessages());
        }
        return super.outboundMessages();
    }

    @Override
    public ChannelFuture writeOneOutbound(final Object msg, final ChannelPromise promise) {
        if (isOutputShutdown.get()) {
            promise.setFailure(newOutputShutdownException());
            return promise;
        }
        return super.writeOneOutbound(msg, promise);
    }

    @Override
    public boolean writeOutbound(final Object... msgs) {
        ensureOutputIsNotShutdown();
        return super.writeOutbound(msgs);
    }

    @Override
    public EmbeddedChannel flushOutbound() {
        ensureOutputIsNotShutdown();
        return super.flushOutbound();
    }

    @Override
    protected void handleOutboundMessage(final Object msg) {
        ensureOutputIsNotShutdown();
        super.handleOutboundMessage(msg);
    }

    private void ensureOutputIsNotShutdown() {
        if (isOutputShutdown.get()) {
            throw newOutputShutdownException();
        }
    }

    private RuntimeException newOutputShutdownException() {
        return new IllegalStateException("Output shutdown");
    }

    @Override
    public ChannelConfig config() {
        if (config == null) {
            // Workaround class initialization sequencing: parent class access config() from the constructor.
            config = new EmbeddedDuplexChannelConfig(this);
        }
        return config;
    }

    private static final class EmbeddedDuplexChannelConfig extends DefaultChannelConfig {

        private volatile boolean allowHalfClosure;

        EmbeddedDuplexChannelConfig(final Channel channel) {
            super(channel);
        }

        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            return getOptions(super.getOptions(), ALLOW_HALF_CLOSURE);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getOption(ChannelOption<T> option) {
            if (option == ALLOW_HALF_CLOSURE) {
                return (T) Boolean.valueOf(isAllowHalfClosure());
            }
            return super.getOption(option);
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            validate(option, value);
            if (option == ALLOW_HALF_CLOSURE) {
                setAllowHalfClosure((Boolean) value);
            } else {
                return super.setOption(option, value);
            }
            return true;
        }

        /**
         * @see SocketChannelConfig#isAllowHalfClosure()
         */
        public boolean isAllowHalfClosure() {
            return allowHalfClosure;
        }

        /**
         * @see SocketChannelConfig#setAllowHalfClosure(boolean)
         */
        public EmbeddedDuplexChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
            this.allowHalfClosure = allowHalfClosure;
            return this;
        }
    }

    // Workaround to get access to AbstractUnsafe.shutdownOutput(Promise):
    @Override
    protected AbstractUnsafe newUnsafe() {
        return unsafe = new EmbeddedUnsafe();
    }

    @Override
    public Unsafe unsafe() {
        assert unsafe != null;
        return unsafe.wrapped;
    }

    // Copied from EmbeddedChannel:
    private final class EmbeddedUnsafe extends AbstractUnsafe {

        // Delegates to the EmbeddedUnsafe instance but ensures runPendingTasks() is called after each operation
        // that may change the state of the Channel and may schedule tasks for later execution.
        final Unsafe wrapped = new Unsafe() {
            @Override
            public RecvByteBufAllocator.Handle recvBufAllocHandle() {
                return EmbeddedUnsafe.this.recvBufAllocHandle();
            }

            @Override
            public SocketAddress localAddress() {
                return EmbeddedUnsafe.this.localAddress();
            }

            @Override
            public SocketAddress remoteAddress() {
                return EmbeddedUnsafe.this.remoteAddress();
            }

            @Override
            public void register(EventLoop eventLoop, ChannelPromise promise) {
                EmbeddedUnsafe.this.register(eventLoop, promise);
                runPendingTasks();
            }

            @Override
            public void bind(SocketAddress localAddress, ChannelPromise promise) {
                EmbeddedUnsafe.this.bind(localAddress, promise);
                runPendingTasks();
            }

            @Override
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                EmbeddedUnsafe.this.connect(remoteAddress, localAddress, promise);
                runPendingTasks();
            }

            @Override
            public void disconnect(ChannelPromise promise) {
                EmbeddedUnsafe.this.disconnect(promise);
                runPendingTasks();
            }

            @Override
            public void close(ChannelPromise promise) {
                EmbeddedUnsafe.this.close(promise);
                runPendingTasks();
            }

            @Override
            public void closeForcibly() {
                EmbeddedUnsafe.this.closeForcibly();
                runPendingTasks();
            }

            @Override
            public void deregister(ChannelPromise promise) {
                EmbeddedUnsafe.this.deregister(promise);
                runPendingTasks();
            }

            @Override
            public void beginRead() {
                EmbeddedUnsafe.this.beginRead();
                runPendingTasks();
            }

            @Override
            public void write(Object msg, ChannelPromise promise) {
                EmbeddedUnsafe.this.write(msg, promise);
                runPendingTasks();
            }

            @Override
            public void flush() {
                EmbeddedUnsafe.this.flush();
                runPendingTasks();
            }

            @Override
            public ChannelPromise voidPromise() {
                return EmbeddedUnsafe.this.voidPromise();
            }

            @Override
            public ChannelOutboundBuffer outboundBuffer() {
                return EmbeddedUnsafe.this.outboundBuffer();
            }
        };

        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            safeSetSuccess(promise);
        }
    }
}
