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
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;

/**
 * {@link EmbeddedChannel} that implements {@link DuplexChannel}.
 */
public final class EmbeddedDuplexChannel extends EmbeddedChannel implements DuplexChannel {

    // Use atomic state because closure or shutdown may be requested from offloaded thread,
    // while EmbeddedEventLoop#inEventLoop() always returns `true`.
    private static final AtomicIntegerFieldUpdater<EmbeddedDuplexChannel> stateUpdater =
            AtomicIntegerFieldUpdater.newUpdater(EmbeddedDuplexChannel.class, "state");

    private static final int STATE_NEW_MASK = 0;
    private static final int STATE_ACTIVE_MASK = 1;
    private static final int STATE_CLOSED_MASK = 1 << 1;
    private static final int STATE_INPUT_SHUTDOWN_MASK = 1 << 2;
    private static final int STATE_OUTPUT_SHUTDOWN_MASK = 1 << 3;
    private static final int STATE_CLOSE_MASK = STATE_CLOSED_MASK |
            STATE_INPUT_SHUTDOWN_MASK |
            STATE_OUTPUT_SHUTDOWN_MASK;

    /**
     * Bit map = [Output Shutdown | Input Shutdown | Closed]
     */
    private volatile int state;

    // Use CountDownLatch instead of ChannelFuture to allow waiting on the main thread, while shutdown is expected to
    // happen on another thread. ChannelFuture considers the main thread as EventLoop thread and does not allow to sync.
    private final CountDownLatch inputShutdownLatch = new CountDownLatch(1);
    private final CountDownLatch outputShutdownLatch = new CountDownLatch(1);

    @Nullable
    private EmbeddedDuplexChannelConfig config;
    @Nullable
    private EmbeddedUnsafe unsafe;

    /**
     * Create a new instance with the pipeline initialized with the specified handlers.
     *
     * @param autoRead {@code true} if the auto-read should be enabled, otherwise {@code false}
     * @param handlers the {@link ChannelHandler}s which will be add in the {@link ChannelPipeline}
     */
    public EmbeddedDuplexChannel(boolean autoRead, ChannelHandler... handlers) {
        super(handlers);
        config().setAutoRead(autoRead);
    }

    private static boolean isActive(int state) {
        return (state & STATE_ACTIVE_MASK) != 0;
    }

    private static boolean isClosed(int state) {
        return (state & STATE_CLOSED_MASK) != 0;
    }

    private static boolean isInputShutdown(int state) {
        return (state & STATE_INPUT_SHUTDOWN_MASK) != 0;
    }

    private static boolean isOutputShutdown(int state) {
        return (state & STATE_OUTPUT_SHUTDOWN_MASK) != 0;
    }

    private static int inputShutdown(int state) {
        return state | STATE_INPUT_SHUTDOWN_MASK;
    }

    private static int outputShutdown(int state) {
        return state | STATE_OUTPUT_SHUTDOWN_MASK;
    }

    private void shutdown(boolean input, boolean output) throws IOException {
        boolean shutdownInput;
        boolean shutdownOutput;
        for (;;) {
            // We need to only shutdown what has not been shutdown yet, and if there is no change we should not
            // shutdown anything.
            final int oldState = this.state;
            if (isClosed(oldState)) {
                throw new ClosedChannelException();
            }

            shutdownInput = false;
            shutdownOutput = false;
            int newState = oldState;
            if (input && !isInputShutdown(newState)) {
                newState = inputShutdown(newState);
                shutdownInput = true;
            }
            if (output && !isOutputShutdown(newState)) {
                newState = outputShutdown(newState);
                shutdownOutput = true;
            }

            // If there is no change in state, then we should not take any action.
            if (newState == oldState) {
                return;
            }
            if (stateUpdater.compareAndSet(this, oldState, newState)) {
                break;
            }
        }
        if (shutdownInput) {
            try {
                pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                super.flushInbound();
                pipeline().fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
            } finally {
                inputShutdownLatch.countDown();
            }
        }
        if (shutdownOutput) {
            try {
                super.flushOutbound();
            } finally {
                outputShutdownLatch.countDown();
            }
        }
    }

    @Override
    public boolean isOpen() {
        return !isClosed(state);
    }

    @Override
    public boolean isActive() {
        return isActive(state);
    }

    @Override
    protected void doRegister() throws Exception {
        stateUpdater.compareAndSet(this, STATE_NEW_MASK, state | STATE_ACTIVE_MASK);
        super.doRegister();
    }

    @Override
    protected void doClose() throws Exception {
        for (;;) {
            final int state = this.state;
            if (isClosed(state)) {
                return;
            }
            // Once a close operation happens, the channel is considered shutdown.
            if (stateUpdater.compareAndSet(this, state, STATE_CLOSE_MASK)) {
                break;
            }
        }
        super.doClose();
        inputShutdownLatch.countDown();
        outputShutdownLatch.countDown();
    }

    @Override
    public boolean isInputShutdown() {
        return isInputShutdown(state);
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

        try {
            shutdown(true, false);
            promise.setSuccess();
        } catch (Throwable cause) {
            promise.setFailure(cause);
        }
        return promise;
    }

    @Override
    public boolean isOutputShutdown() {
        return isOutputShutdown(state);
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
    protected void doShutdownOutput() throws Exception {
        shutdown(false, true);
    }

    @Override
    public boolean isShutdown() {
        final int state = this.state;
        return isInputShutdown(state) && isOutputShutdown(state);
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
        if (isInputShutdown()) {
            // Best effort to prevent external manipulations of internal inboundMessages queue:
            return new ArrayDeque<>(super.inboundMessages());
        }
        return super.inboundMessages();
    }

    @Override
    public ChannelFuture writeOneInbound(final Object msg, final ChannelPromise promise) {
        if (isInputShutdown()) {
            // Ignore new inbound
            ReferenceCountUtil.safeRelease(msg);
            return promise.setSuccess();
        }
        return super.writeOneInbound(msg, promise);
    }

    @Override
    public boolean writeInbound(final Object... msgs) {
        if (isInputShutdown()) {
            // Ignore new inbound
            for (Object msg : msgs) {
                ReferenceCountUtil.safeRelease(msg);
            }
            return false;
        }
        return super.writeInbound(msgs);
    }

    @Override
    public EmbeddedChannel flushInbound() {
        if (isInputShutdown()) {
            // Ignore new inbound
            return this;
        }
        return super.flushInbound();
    }

    @Override
    protected void handleInboundMessage(final Object msg) {
        if (isInputShutdown()) {
            // Ignore new inbound
            ReferenceCountUtil.safeRelease(msg);
            return;
        }
        super.handleInboundMessage(msg);
    }

    @Override
    public Queue<Object> outboundMessages() {
        if (isOutputShutdown()) {
            // Best effort to prevent external manipulations of internal outboundMessages queue:
            return new ArrayDeque<>(super.outboundMessages());
        }
        return super.outboundMessages();
    }

    @Override
    public ChannelFuture writeOneOutbound(final Object msg, final ChannelPromise promise) {
        if (isOutputShutdown()) {
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
        if (isOutputShutdown()) {
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
