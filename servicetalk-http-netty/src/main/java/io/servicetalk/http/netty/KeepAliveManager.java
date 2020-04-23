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
package io.servicetalk.http.netty;

import io.servicetalk.http.netty.H2ProtocolConfig.KeepAlivePolicy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.servicetalk.http.netty.DefaultKeepAlivePolicy.DEFAULT_ACK_TIMEOUT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * An implementation of {@link KeepAlivePolicy} per {@link Channel}.
 */
final class KeepAliveManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeepAliveManager.class);
    private static final AtomicIntegerFieldUpdater<KeepAliveManager> activeChildChannelsUpdater =
            AtomicIntegerFieldUpdater.newUpdater(KeepAliveManager.class, "activeChildChannels");
    private static final long GRACEFUL_CLOSE_PING_CONTENT = ThreadLocalRandom.current().nextLong();
    private static final long KEEP_ALIVE_PING_CONTENT = ThreadLocalRandom.current().nextLong();
    private static final Object CLOSED = new Object();
    private static final Object GRACEFUL_CLOSE_START = new Object();
    private static final Object GRACEFUL_CLOSE_SECOND_GO_AWAY_SENT = new Object();
    private static final Object KEEP_ALIVE_ACK_PENDING = new Object();
    private static final Object KEEP_ALIVE_ACK_TIMEDOUT = new Object();

    private volatile int activeChildChannels;

    private final Channel channel;
    private final long pingAckTimeoutMillis;
    private final boolean disallowKeepAliveWithoutActiveStreams;
    private final Scheduler scheduler;

    // below state should only be accessed from eventloop
    /**
     * This stores the following possible values:
     * <ul>
     *     <li>{@code null} if graceful close has not started.</li>
     *     <li>{@link #GRACEFUL_CLOSE_START} if graceful close process has been initiated.</li>
     *     <li>{@link Future} instance to timeout ack of PING sent to measure RTT.</li>
     *     <li>{@link #GRACEFUL_CLOSE_SECOND_GO_AWAY_SENT} if we have sent the second go away frame.</li>
     *     <li>{@link #CLOSED} if the channel is closed.</li>
     * </ul>
     */
    @Nullable
    private Object gracefulCloseState;

    /**
     * This stores the following possible values:
     * <ul>
     *     <li>{@code null} if keep-alive PING process is not started.</li>
     *     <li>{@link #KEEP_ALIVE_ACK_PENDING} if a keep-alive PING has been sent but ack is not received.</li>
     *     <li>{@link Future} instance to timeout ack of PING sent.</li>
     *     <li>{@link #KEEP_ALIVE_ACK_TIMEDOUT} if we fail to receive a PING ack for the configured timeout.</li>
     *     <li>{@link #CLOSED} if the channel is closed.</li>
     * </ul>
     */
    @Nullable
    private Object keepAliveState;
    @Nullable
    private final GenericFutureListener<Future<? super Void>> pingWriteCompletionListener;

    KeepAliveManager(final Channel channel, @Nullable final KeepAlivePolicy keepAlivePolicy) {
        this(channel, keepAlivePolicy, (task, delayInMillis) ->
                channel.eventLoop().schedule(task, delayInMillis, MILLISECONDS),
                (ch, idlenessThresholdSeconds, onIdle) -> ch.pipeline().addLast(
                        new IdleStateHandler(idlenessThresholdSeconds, idlenessThresholdSeconds, 0) {
                            @Override
                            protected void channelIdle(final ChannelHandlerContext ctx, final IdleStateEvent evt) {
                                onIdle.run();
                            }
                        }));
    }

    KeepAliveManager(final Channel channel, @Nullable final KeepAlivePolicy keepAlivePolicy,
                     final Scheduler scheduler, final IdlenessDetector idlenessDetector) {
        this.channel = channel;
        this.scheduler = scheduler;
        if (keepAlivePolicy != null) {
            disallowKeepAliveWithoutActiveStreams = !keepAlivePolicy.withoutActiveStreams();
            pingAckTimeoutMillis = keepAlivePolicy.ackTimeout().toMillis();
            final GenericFutureListener<Future<? super Void>> goAwayListener = f -> {
                if (f.isSuccess()) {
                    LOGGER.debug("Closing channel={}, after keep-alive timeout.", this.channel);
                    KeepAliveManager.this.close0();
                }
            };
            pingWriteCompletionListener = future -> {
                if (future.isSuccess() && keepAliveState == KEEP_ALIVE_ACK_PENDING) {
                    // Schedule a task to verify ping ack within the pingAckTimeoutMillis
                    keepAliveState = scheduler.afterMillis(() -> {
                        if (keepAliveState != null) {
                            keepAliveState = KEEP_ALIVE_ACK_TIMEDOUT;
                            LOGGER.debug(
                                    "channel={}, timeout {}ms waiting for keep-alive PING(ACK), writing go_away.",
                                    this.channel, pingAckTimeoutMillis);
                            channel.writeAndFlush(new DefaultHttp2GoAwayFrame(NO_ERROR))
                                    .addListener(goAwayListener);
                        }
                    }, pingAckTimeoutMillis);
                }
            };
            int idleInSeconds = (int) keepAlivePolicy.idleDuration().getSeconds();
            idlenessDetector.configure(channel, idleInSeconds, this::channelIdle);
        } else {
            disallowKeepAliveWithoutActiveStreams = false;
            pingAckTimeoutMillis = DEFAULT_ACK_TIMEOUT.toMillis();
            pingWriteCompletionListener = null;
        }
    }

    void pingReceived(final Http2PingFrame pingFrame) {
        assert channel.eventLoop().inEventLoop();

        if (pingFrame.ack()) {
            long pingAckContent = pingFrame.content();
            if (pingAckContent == GRACEFUL_CLOSE_PING_CONTENT) {
                LOGGER.debug("channel={}, graceful close ping ack received.", channel);
                cancelIfStateIsAFuture(gracefulCloseState);
                gracefulCloseWriteSecondGoAway();
            } else if (pingAckContent == KEEP_ALIVE_PING_CONTENT) {
                cancelIfStateIsAFuture(keepAliveState);
                keepAliveState = null;
            }
        } else {
            // Send an ack for the received ping
            channel.writeAndFlush(new DefaultHttp2PingFrame(pingFrame.content(), true));
        }
    }

    void trackActiveStream(final Channel streamChannel) {
        activeChildChannelsUpdater.incrementAndGet(this);
        streamChannel.closeFuture().addListener(f -> {
            activeChildChannelsUpdater.decrementAndGet(this);
            if (activeChildChannels == 0 && gracefulCloseState == GRACEFUL_CLOSE_SECOND_GO_AWAY_SENT) {
                close0();
            }
        });
    }

    void channelClosed() {
        assert channel.eventLoop().inEventLoop();

        cancelIfStateIsAFuture(gracefulCloseState);
        cancelIfStateIsAFuture(keepAliveState);
        gracefulCloseState = CLOSED;
        keepAliveState = CLOSED;
    }

    void initiateGracefulClose(final Runnable whenInitiated) {
        EventLoop eventLoop = channel.eventLoop();
        if (eventLoop.inEventLoop()) {
            doCloseAsyncGracefully0(whenInitiated);
        } else {
            try {
                eventLoop.execute(() -> doCloseAsyncGracefully0(whenInitiated));
            } catch (RejectedExecutionException ree) {
                close0();
                LOGGER.warn("channel={} EventLoop rejected a task for graceful shutdown, force closing connection",
                        channel, ree);
            }
        }
    }

    void channelIdle() {
        assert channel.eventLoop().inEventLoop();
        assert pingWriteCompletionListener != null;

        if (keepAliveState != null || activeChildChannels == 0 && disallowKeepAliveWithoutActiveStreams) {
            return;
        }
        // idleness detected for the first time, send a ping to detect closure, if any.
        keepAliveState = KEEP_ALIVE_ACK_PENDING;
        channel.writeAndFlush(new DefaultHttp2PingFrame(KEEP_ALIVE_PING_CONTENT, false))
                .addListener(pingWriteCompletionListener);
    }

    /**
     * Scheduler of {@link Runnable}s.
     */
    @FunctionalInterface
    interface Scheduler {

        /**
         * Run the passed {@link Runnable} after {@code delayInMillis} milliseconds.
         *
         * @param task {@link Runnable} to run.
         * @param delayInMillis Milliseconds after which the task is to be run.
         * @return {@link Future} for the scheduled task.
         */
        Future<?> afterMillis(Runnable task, long delayInMillis);
    }

    /**
     * Scheduler of {@link Runnable}s.
     */
    @FunctionalInterface
    interface IdlenessDetector {
        /**
         * Configure idleness detection for the passed {@code channel}.
         *
         * @param channel {@link Channel} for which idleness detection is to be configured.
         * @param idlenessThresholdSeconds Seconds of idleness after which {@link Runnable#run()} should be called on
         * the passed {@code onIdle}.
         * @param onIdle {@link Runnable} to call when the channel is idle more than {@code idlenessThresholdSeconds}.
         */
        void configure(Channel channel, int idlenessThresholdSeconds, Runnable onIdle);
    }

    private void doCloseAsyncGracefully0(final Runnable whenInitiated) {
        assert channel.eventLoop().inEventLoop();

        if (gracefulCloseState != null) {
            // either we are already closed or have already initiated graceful closure.
            return;
        }

        whenInitiated.run();

        // Set the pingState before doing the write, because we will reference the state
        // when we receive the PING(ACK) to determine if action is necessary, and it is conceivable that the
        // write future may not be executed which sets the timer.
        gracefulCloseState = GRACEFUL_CLOSE_START;

        // The graceful close process is described in [1]. It involves sending 2 GOAWAY frames. The first
        // GOAWAY has last-stream-id=<maximum stream ID> to indicate no new streams can be created, wait for 2 RTT
        // time duration for inflight frames to land, and the second GOAWAY includes the maximum known stream ID.
        // To account for 2 RTTs we can send a PING and when the PING(ACK) comes back we can send the second GOAWAY.
        // [1] https://tools.ietf.org/html/rfc7540#section-6.8
        DefaultHttp2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(NO_ERROR);
        goAwayFrame.setExtraStreamIds(Integer.MAX_VALUE);
        channel.write(goAwayFrame);
        channel.writeAndFlush(new DefaultHttp2PingFrame(GRACEFUL_CLOSE_PING_CONTENT)).addListener(future -> {
            // If gracefulCloseState is not GRACEFUL_CLOSE_START that means we have already received the PING(ACK) and
            // there is no need to apply the timeout.
            if (future.isSuccess() && gracefulCloseState == GRACEFUL_CLOSE_START) {
                gracefulCloseState = scheduler.afterMillis(() -> {
                    // If the PING(ACK) times out we may have under estimated the 2RTT time so we
                    // optimistically keep the connection open and rely upon higher level timeouts to tear
                    // down the connection.
                    LOGGER.debug("channel={} timeout {}ms waiting for PING(ACK) during graceful close.",
                            channel, pingAckTimeoutMillis);
                    gracefulCloseWriteSecondGoAway();
                }, pingAckTimeoutMillis);
            }
        });
    }

    private void gracefulCloseWriteSecondGoAway() {
        assert channel.eventLoop().inEventLoop();

        if (gracefulCloseState == GRACEFUL_CLOSE_SECOND_GO_AWAY_SENT) {
            return;
        }

        gracefulCloseState = GRACEFUL_CLOSE_SECOND_GO_AWAY_SENT;

        channel.writeAndFlush(new DefaultHttp2GoAwayFrame(NO_ERROR)).addListener(future -> {
            if (activeChildChannels == 0) {
                close0();
            }
        });
    }

    private void close0() {
        assert channel.eventLoop().inEventLoop();

        if (gracefulCloseState == CLOSED && keepAliveState == CLOSED) {
            return;
        }
        gracefulCloseState = CLOSED;
        keepAliveState = CLOSED;

        // The way netty H2 stream state machine works, we may trigger stream closures during writes with flushes
        // pending behind the writes. In such cases, we may close too early ignoring the writes. Hence we flush before
        // closure, if there is no write pending then flush is a noop.
        channel.flush();
        channel.close();
    }

    private void cancelIfStateIsAFuture(@Nullable final Object state) {
        if (state instanceof Future) {
            try {
                ((Future<?>) state).cancel(true);
            } catch (Throwable t) {
                LOGGER.debug("Failed to cancel {} scheduled future.",
                        state == keepAliveState ? "keep-alive" : "graceful close", t);
            }
        }
    }
}
