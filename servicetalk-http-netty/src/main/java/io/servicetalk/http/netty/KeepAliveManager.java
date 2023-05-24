/*
 * Copyright © 2020, 2023 Apple Inc. and the ServiceTalk project authors
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
import io.netty.channel.socket.DuplexChannel;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.DEFAULT_ACK_TIMEOUT;
import static java.lang.Boolean.TRUE;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * An implementation of {@link KeepAlivePolicy} per {@link Channel}.
 */
final class KeepAliveManager {
    private enum State {
        GRACEFUL_CLOSE_START,
        GRACEFUL_CLOSE_SECOND_GO_AWAY_SENT,
        KEEP_ALIVE_ACK_PENDING,
        KEEP_ALIVE_ACK_TIMEDOUT,
        CLOSED
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(KeepAliveManager.class);
    private static final AtomicIntegerFieldUpdater<KeepAliveManager> activeStreamsUpdater =
            AtomicIntegerFieldUpdater.newUpdater(KeepAliveManager.class, "activeStreams");
    private static final long GRACEFUL_CLOSE_PING_CONTENT = ThreadLocalRandom.current().nextLong();
    private static final long KEEP_ALIVE_PING_CONTENT = ThreadLocalRandom.current().nextLong();

    private volatile int activeStreams;

    private final Channel channel;
    private final long pingAckTimeoutNanos;
    private final boolean disallowKeepAliveWithoutActiveStreams;
    private final Scheduler scheduler;

    // below state should only be accessed from eventloop
    /**
     * This stores the following possible values:
     * <ul>
     *     <li>{@code null} if graceful close has not started.</li>
     *     <li>{@link State#GRACEFUL_CLOSE_START} if graceful close process has been initiated.</li>
     *     <li>{@link Future} instance to timeout ack of PING sent to measure RTT.</li>
     *     <li>{@link State#GRACEFUL_CLOSE_SECOND_GO_AWAY_SENT} if we have sent the second go away frame.</li>
     *     <li>{@link State#CLOSED} if the channel is closed.</li>
     * </ul>
     */
    @Nullable
    private Object gracefulCloseState;

    /**
     * This stores the following possible values:
     * <ul>
     *     <li>{@code null} if keep-alive PING process is not started.</li>
     *     <li>{@link State#KEEP_ALIVE_ACK_PENDING} if a keep-alive PING has been sent but ack is not received.</li>
     *     <li>{@link Future} instance to timeout ack of PING sent.</li>
     *     <li>{@link State#KEEP_ALIVE_ACK_TIMEDOUT} if we fail to receive a PING ack for the configured timeout.</li>
     *     <li>{@link State#CLOSED} if the channel is closed.</li>
     * </ul>
     */
    @Nullable
    private Object keepAliveState;
    @Nullable
    private final GenericFutureListener<Future<? super Void>> pingWriteCompletionListener;

    KeepAliveManager(final Channel channel, @Nullable final KeepAlivePolicy keepAlivePolicy) {
        this(channel, keepAlivePolicy, (task, delay, unit) ->
                        channel.eventLoop().schedule(task, delay, unit),
                (ch, idlenessThresholdNanos, onIdle) -> ch.pipeline().addLast(
                        new IdleStateHandler(0, 0, idlenessThresholdNanos, NANOSECONDS) {
                            @Override
                            protected void channelIdle(final ChannelHandlerContext ctx, final IdleStateEvent evt) {
                                onIdle.run();
                            }
                        }));
    }

    KeepAliveManager(final Channel channel, @Nullable final KeepAlivePolicy keepAlivePolicy,
                     final Scheduler scheduler, final IdlenessDetector idlenessDetector) {
        if (channel instanceof DuplexChannel) {
            channel.config().setOption(ALLOW_HALF_CLOSURE, TRUE);
            channel.config().setAutoClose(false);
        }
        this.channel = channel;
        this.scheduler = scheduler;
        // Before 0.42.30, H2ProtocolConfig.keepAlivePolicy() was @Nullable. For backward compatibility, we keep
        // tolerance for null values.
        if (keepAlivePolicy != null) {  // FIXME: 0.43.x - consider removing null check
            // KeepAlivePolicy with idlenessThresholdNanos <= 0 disables PINGs, but allows configuring
            // pingAckTimeoutNanos for graceful closure (GO_AWAY).
            disallowKeepAliveWithoutActiveStreams = !keepAlivePolicy.withoutActiveStreams();
            pingAckTimeoutNanos = keepAlivePolicy.ackTimeout().toNanos();
            final long idlenessThresholdNanos = keepAlivePolicy.idleDuration().toNanos();
            pingWriteCompletionListener = idlenessThresholdNanos > 0 ? future -> {
                assert channel.eventLoop().inEventLoop();
                if (!future.isSuccess()) {
                    LOGGER.debug("{} Failed to write a PING frame after idleness is detected, closing the channel",
                            channel, future.cause());
                    close0(future.cause());
                } else if (keepAliveState == State.KEEP_ALIVE_ACK_PENDING) {
                    // Schedule a task to verify ping ack within the pingAckTimeoutMillis
                    keepAliveState = scheduler.afterDuration(() -> {
                        if (keepAliveState != null) {
                            keepAliveState = State.KEEP_ALIVE_ACK_TIMEDOUT;
                            LOGGER.debug(
                                    "{} Timeout after {}ms waiting for keep-alive PING(ACK), writing GO_AWAY and " +
                                            "closing the channel",
                                    this.channel, NANOSECONDS.toMillis(pingAckTimeoutNanos));
                            channel.writeAndFlush(new DefaultHttp2GoAwayFrame(NO_ERROR))
                                    .addListener(f -> {
                                        if (!f.isSuccess()) {
                                            LOGGER.debug("{} Failed to write the last GO_AWAY after PING(ACK) " +
                                                            "timeout, closing the channel", channel, f.cause());
                                        }
                                        close0(f.cause());
                                    });
                        }
                    }, pingAckTimeoutNanos, NANOSECONDS);
                }
            } : null;
            if (idlenessThresholdNanos > 0) {
                idlenessDetector.configure(channel, idlenessThresholdNanos, this::channelIdle);
            }
        } else {
            disallowKeepAliveWithoutActiveStreams = false;
            pingAckTimeoutNanos = DEFAULT_ACK_TIMEOUT.toNanos();
            pingWriteCompletionListener = null;
        }
        LOGGER.debug("{} Configured for {}duplex channel with policy={}",
                channel, channel instanceof DuplexChannel ? "" : "non-", keepAlivePolicy);
    }

    void pingReceived(final Http2PingFrame pingFrame) {
        assert channel.eventLoop().inEventLoop();

        if (pingFrame.ack()) {
            long pingAckContent = pingFrame.content();
            if (pingAckContent == GRACEFUL_CLOSE_PING_CONTENT) {
                LOGGER.debug("{} Graceful close PING(ACK) received, writing the second GO_AWAY, activeStreams={}",
                        channel, activeStreams);
                cancelIfStateIsAFuture(gracefulCloseState);
                gracefulCloseWriteSecondGoAway();
            } else if (pingAckContent == KEEP_ALIVE_PING_CONTENT) {
                LOGGER.trace("{} PING(ACK) received, activeStreams={}", channel, activeStreams);
                cancelIfStateIsAFuture(keepAliveState);
                keepAliveState = null;
            }
        } else {
            // Send an ack for the received ping
            channel.writeAndFlush(new DefaultHttp2PingFrame(pingFrame.content(), true));
        }
    }

    void trackActiveStream(final Http2StreamChannel streamChannel) {
        activeStreamsUpdater.incrementAndGet(this);
        streamChannel.closeFuture().addListener(f -> {
            if (activeStreamsUpdater.decrementAndGet(this) == 0 &&
                    gracefulCloseState == State.GRACEFUL_CLOSE_SECOND_GO_AWAY_SENT) {
                // close0 needs to know only about write failures, always pass null when closeFuture completes
                close0(null);
            }
        });
    }

    void channelClosed() {
        assert channel.eventLoop().inEventLoop();
        LOGGER.debug("{} Channel closed with activeStreams={}, gracefulCloseState={}, keepAliveState={}",
                channel, activeStreams, gracefulCloseState, keepAliveState);

        cancelIfStateIsAFuture(gracefulCloseState);
        cancelIfStateIsAFuture(keepAliveState);
        gracefulCloseState = State.CLOSED;
        keepAliveState = State.CLOSED;
    }

    void initiateGracefulClose(final Runnable whenInitiated) {
        EventLoop eventLoop = channel.eventLoop();
        if (eventLoop.inEventLoop()) {
            doCloseAsyncGracefully0(whenInitiated);
        } else {
            eventLoop.execute(() -> doCloseAsyncGracefully0(whenInitiated));
        }
    }

    void channelIdle() {
        assert channel.eventLoop().inEventLoop();
        assert pingWriteCompletionListener != null;

        if (keepAliveState != null || disallowKeepAliveWithoutActiveStreams && activeStreams == 0) {
            return;
        }
        LOGGER.debug("{} Idleness detected with activeStreams={}", channel, activeStreams);
        // idleness detected for the first time, send a ping to detect closure, if any.
        keepAliveState = State.KEEP_ALIVE_ACK_PENDING;
        channel.writeAndFlush(new DefaultHttp2PingFrame(KEEP_ALIVE_PING_CONTENT, false))
                .addListener(pingWriteCompletionListener);
    }

    void channelOutputShutdown() {
        assert channel.eventLoop().inEventLoop();
        channelHalfShutdown("output", DuplexChannel::isInputShutdown);
    }

    void channelInputShutdown() {
        assert channel.eventLoop().inEventLoop();
        channelHalfShutdown("input", DuplexChannel::isOutputShutdown);
    }

    /**
     * Scheduler of {@link Runnable}s.
     */
    @FunctionalInterface
    interface Scheduler {

        /**
         * Run the passed {@link Runnable} after {@code delay} milliseconds.
         *
         * @param task {@link Runnable} to run.
         * @param delay after which the task is to be run.
         * @param unit {@link TimeUnit} for the delay.
         * @return {@link Future} for the scheduled task.
         */
        Future<?> afterDuration(Runnable task, long delay, TimeUnit unit);
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
         * @param idlenessThresholdNanos Nanoseconds of idleness after which {@link Runnable#run()} should be called on
         * the passed {@code onIdle}.
         * @param onIdle {@link Runnable} to call when the channel is idle more than {@code idlenessThresholdNanos}.
         */
        void configure(Channel channel, long idlenessThresholdNanos, Runnable onIdle);
    }

    private void channelHalfShutdown(String side, Predicate<DuplexChannel> otherSideShutdown) {
        if (channel instanceof DuplexChannel) {
            final DuplexChannel duplexChannel = (DuplexChannel) channel;
            if (otherSideShutdown.test(duplexChannel)) {
                LOGGER.debug("{} Observed {} shutdown, other side is shutdown too, closing the channel with " +
                                "activeStreams={}, gracefulCloseState={}, keepAliveState={}",
                        channel, side, activeStreams, gracefulCloseState, keepAliveState);
                channel.close();
            } else if (gracefulCloseState != State.GRACEFUL_CLOSE_SECOND_GO_AWAY_SENT &&
                    gracefulCloseState != State.CLOSED) {
                // If we have not started the graceful close process, or waiting for ack/read to complete the graceful
                // close process just force a close now because we will not read any more data.
                LOGGER.debug("{} Observed {} shutdown, graceful close is not started or in progress, must force " +
                                "channel closure with activeStreams={}, gracefulCloseState={}, keepAliveState={}",
                        channel, side, activeStreams, gracefulCloseState, keepAliveState);
                channel.close();
            }
        } else {
            channel.close();
        }
    }

    private void doCloseAsyncGracefully0(final Runnable whenInitiated) {
        assert channel.eventLoop().inEventLoop();

        if (gracefulCloseState != null) {
            // either we are already closed or have already initiated graceful closure.
            return;
        }
        LOGGER.debug("{} Close gracefully with activeStreams={}, keepAliveState={}",
                channel, activeStreams, keepAliveState);

        whenInitiated.run();

        // Set the pingState before doing the write, because we will reference the state
        // when we receive the PING(ACK) to determine if action is necessary, and it is conceivable that the
        // write future may not be executed which sets the timer.
        gracefulCloseState = State.GRACEFUL_CLOSE_START;

        // The graceful close process is described in [1]. It involves sending 2 GOAWAY frames. The first
        // GOAWAY has last-stream-id=<maximum stream ID> to indicate no new streams can be created, wait for 2 RTT
        // time duration for inflight frames to land, and the second GOAWAY includes the maximum known stream ID.
        // To account for 2 RTTs we can send a PING and when the PING(ACK) comes back we can send the second GOAWAY.
        // [1] https://tools.ietf.org/html/rfc7540#section-6.8
        DefaultHttp2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(NO_ERROR);
        goAwayFrame.setExtraStreamIds(Integer.MAX_VALUE);
        channel.write(goAwayFrame);
        channel.writeAndFlush(new DefaultHttp2PingFrame(GRACEFUL_CLOSE_PING_CONTENT)).addListener(future -> {
            assert channel.eventLoop().inEventLoop();
            if (!future.isSuccess()) {
                LOGGER.debug("{} Failed to write the first GO_AWAY and PING frames, closing the channel",
                        channel, future.cause());
                close0(future.cause());
            } else if (gracefulCloseState == State.GRACEFUL_CLOSE_START) {
                // If gracefulCloseState is not GRACEFUL_CLOSE_START that means we have already received the PING(ACK)
                // and there is no need to apply the timeout.
                gracefulCloseState = scheduler.afterDuration(() -> {
                    // If the PING(ACK) times out we may have under estimated the 2RTT time so we
                    // optimistically keep the connection open and rely upon higher level timeouts to tear
                    // down the connection.
                    LOGGER.debug(
                            "{} Timeout after {}ms waiting for graceful close PING(ACK), writing the second GO_AWAY",
                            channel, NANOSECONDS.toMillis(pingAckTimeoutNanos));
                    gracefulCloseWriteSecondGoAway();
                }, pingAckTimeoutNanos, NANOSECONDS);
            }
        });
    }

    private void gracefulCloseWriteSecondGoAway() {
        assert channel.eventLoop().inEventLoop();

        if (gracefulCloseState == State.GRACEFUL_CLOSE_SECOND_GO_AWAY_SENT) {
            return;
        }

        gracefulCloseState = State.GRACEFUL_CLOSE_SECOND_GO_AWAY_SENT;

        channel.writeAndFlush(new DefaultHttp2GoAwayFrame(NO_ERROR)).addListener(future -> {
            if (!future.isSuccess()) {
                LOGGER.debug("{} Failed to write the second GO_AWAY, closing the channel", channel, future.cause());
                close0(future.cause());
            } else if (activeStreams == 0) {
                close0(null);
            }
        });
    }

    private void close0(@Nullable Throwable cause) {
        assert channel.eventLoop().inEventLoop();

        if (gracefulCloseState == State.CLOSED && keepAliveState == State.CLOSED) {
            return;
        }
        LOGGER.debug("{} Marking all states as CLOSED with activeStreams={}, gracefulCloseState={}, keepAliveState={}",
                channel, activeStreams, gracefulCloseState, keepAliveState);
        gracefulCloseState = State.CLOSED;
        keepAliveState = State.CLOSED;

        if (cause != null) {
            // Previous write failed with an exception, close immediately.
            closeNotifyAndShutdownOutput();
            return;
        }
        // The way netty H2 stream state machine works, we may trigger stream closures during writes with flushes
        // pending behind the writes. In such cases, we may close too early ignoring the writes. Hence we flush before
        // closure, if there is no write pending then flush is a noop.
        channel.writeAndFlush(EMPTY_BUFFER).addListener(f -> closeNotifyAndShutdownOutput());
    }

    private void closeNotifyAndShutdownOutput() {
        SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
        if (sslHandler != null) {
            // send close_notify: https://tools.ietf.org/html/rfc5246#section-7.2.1
            sslHandler.closeOutbound().addListener(f2 -> doShutdownOutput());
        } else {
            doShutdownOutput();
        }
    }

    private void doShutdownOutput() {
        if (channel instanceof DuplexChannel) {
            final DuplexChannel duplexChannel = (DuplexChannel) channel;
            duplexChannel.shutdownOutput().addListener(f -> {
                if (duplexChannel.isInputShutdown()) {
                    LOGGER.debug("{} Input and output shutdown, closing the channel with activeStreams={}",
                            channel, activeStreams);
                    channel.close();
                }
            });
        } else {
            channel.close();
        }
    }

    private void cancelIfStateIsAFuture(@Nullable final Object state) {
        if (state instanceof Future) {
            try {
                ((Future<?>) state).cancel(true);
            } catch (Throwable t) {
                LOGGER.debug("{} Failed to cancel {} scheduled future",
                        channel, state == keepAliveState ? "keep-alive" : "graceful close", t);
            }
        }
    }
}
