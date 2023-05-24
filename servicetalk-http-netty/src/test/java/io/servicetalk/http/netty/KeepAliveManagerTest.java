/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.netty.internal.EmbeddedDuplexChannel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.handler.codec.http2.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.util.concurrent.Promise;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.DEFAULT_ACK_TIMEOUT;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.DEFAULT_IDLE_DURATION;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeepAliveManagerTest {

    private final BlockingQueue<ScheduledTask> scheduledTasks = new LinkedBlockingQueue<>();
    private EmbeddedChannel channel;
    private KeepAliveManager manager;

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    private void setUp(boolean duplex, boolean allowPingWithoutActiveStreams) {
        KeepAliveManagerHandler managerHandler = new KeepAliveManagerHandler();
        channel = duplex ? new EmbeddedDuplexChannel(true, managerHandler) : new EmbeddedChannel(managerHandler);
        manager = newManager(allowPingWithoutActiveStreams, channel);
        managerHandler.keepAliveManager(manager);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void keepAliveDisallowedWithNoActiveStreams(boolean duplex) {
        setUp(duplex, false);
        manager.channelIdle();
        verifyNoWrite();
        verifyNoScheduledTasks();
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void keepAliveAllowedWithNoActiveStreams(boolean duplex) {
        setUp(duplex, true);
        manager.channelIdle();
        verifyWrite(instanceOf(Http2PingFrame.class));
        verifyPingAckTimeoutScheduled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void keepAliveWithActiveStreams(boolean duplex) {
        setUp(duplex, false);
        addActiveStream(manager);
        manager.channelIdle();
        verifyWrite(instanceOf(Http2PingFrame.class));
        verifyPingAckTimeoutScheduled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void keepAlivePingAckReceived(boolean duplex) {
        setUp(duplex, false);
        addActiveStream(manager);
        manager.channelIdle();
        Http2PingFrame ping = verifyWrite(instanceOf(Http2PingFrame.class));
        ScheduledTask ackTimeoutTask = verifyPingAckTimeoutScheduled();

        manager.pingReceived(new DefaultHttp2PingFrame(ping.content(), true));
        assertThat("Ping ack timeout task not cancelled.", ackTimeoutTask.promise.isCancelled(), is(true));

        ackTimeoutTask.task.run();
        verifyNoWrite();
        verifyNoScheduledTasks();
        assertThat("Channel unexpectedly closed.", channel.isOpen(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void keepAlivePingAckWithUnknownContent(boolean duplex) throws Exception {
        setUp(duplex, false);
        addActiveStream(manager);
        manager.channelIdle();
        Http2PingFrame ping = verifyWrite(instanceOf(Http2PingFrame.class));
        ScheduledTask ackTimeoutTask = verifyPingAckTimeoutScheduled();

        manager.pingReceived(new DefaultHttp2PingFrame(ping.content() + 1, true));
        assertThat("Ping ack timeout task cancelled.", ackTimeoutTask.promise.isCancelled(), is(false));

        verifyChannelCloseOnMissingPingAck(ackTimeoutTask);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void keepAliveMissingPingAck(boolean duplex) throws Exception {
        setUp(duplex, false);
        addActiveStream(manager);
        manager.channelIdle();
        verifyWrite(instanceOf(Http2PingFrame.class));
        verifyChannelCloseOnMissingPingAck(verifyPingAckTimeoutScheduled());
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void gracefulCloseNoActiveStreams(boolean duplex) throws Exception {
        setUp(duplex, false);
        Http2PingFrame pingFrame = initiateGracefulCloseVerifyGoAwayAndPing(manager);

        sendGracefulClosePingAckAndVerifySecondGoAway(manager, pingFrame);
        shutdownInputIfDuplexChannel();
        channel.closeFuture().sync().await();
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void gracefulCloseWithActiveStreams(boolean duplex) throws Exception {
        setUp(duplex, false);
        Http2StreamChannel activeStream = addActiveStream(manager);
        Http2PingFrame pingFrame = initiateGracefulCloseVerifyGoAwayAndPing(manager);

        sendGracefulClosePingAckAndVerifySecondGoAway(manager, pingFrame);

        assertThat("Channel not closed.", channel.isOpen(), is(true));
        activeStream.close().sync().await();
        shutdownInputIfDuplexChannel();
        channel.closeFuture().sync().await();
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void gracefulCloseNoActiveStreamsMissingPingAck(boolean duplex) throws Exception {
        setUp(duplex, false);
        initiateGracefulCloseVerifyGoAwayAndPing(manager);

        ScheduledTask pingAckTimeoutTask = scheduledTasks.take();
        pingAckTimeoutTask.task.run();
        verifySecondGoAway();
        shutdownInputIfDuplexChannel();
        channel.closeFuture().sync().await();
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void gracefulCloseActiveStreamsMissingPingAck(boolean duplex) throws Exception {
        setUp(duplex, false);
        Http2StreamChannel activeStream = addActiveStream(manager);
        initiateGracefulCloseVerifyGoAwayAndPing(manager);

        ScheduledTask pingAckTimeoutTask = scheduledTasks.take();
        pingAckTimeoutTask.task.run();
        verifySecondGoAway();

        assertThat("Channel closed.", channel.isOpen(), is(true));
        activeStream.close().sync().await();
        shutdownInputIfDuplexChannel();
        channel.closeFuture().sync().await();
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void gracefulClosePendingPingsCloseConnection(boolean duplex) throws Exception {
        setUp(duplex, false);
        addActiveStream(manager);
        Http2PingFrame pingFrame = initiateGracefulCloseVerifyGoAwayAndPing(manager);

        sendGracefulClosePingAckAndVerifySecondGoAway(manager, pingFrame);
        assertThat("Channel closed.", channel.isOpen(), is(true));

        manager.channelIdle();
        verifyWrite(instanceOf(Http2PingFrame.class));
        verifyChannelCloseOnMissingPingAck(verifyPingAckTimeoutScheduled());
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void pingsAreAcked(boolean duplex) {
        setUp(duplex, false);
        long pingContent = ThreadLocalRandom.current().nextLong();
        manager.pingReceived(new DefaultHttp2PingFrame(pingContent, false));
        Http2PingFrame pingFrame = verifyWrite(instanceOf(Http2PingFrame.class));
        assertThat("Unexpected ping ack content.", pingFrame.content(), is(pingContent));
        assertThat("Unexpected ping ack flag.", pingFrame.ack(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void channelClosedDuringGracefulClose(boolean duplex) throws Exception {
        setUp(duplex, false);
        addActiveStream(manager);
        initiateGracefulCloseVerifyGoAwayAndPing(manager);
        ScheduledTask pingAckTimeoutTask = scheduledTasks.take();
        assertThat("Ping ack timeout not scheduled.", pingAckTimeoutTask, is(notNullValue()));

        manager.channelClosed();
        assertThat("Graceful close ping ack timeout not cancelled.", pingAckTimeoutTask.promise.isCancelled(),
                is(true));

        verifyNoOtherActionPostClose(manager);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void channelClosedDuringPing(boolean duplex) {
        setUp(duplex, false);
        addActiveStream(manager);
        manager.channelIdle();
        verifyWrite(instanceOf(Http2PingFrame.class));
        ScheduledTask ackTimeoutTask = verifyPingAckTimeoutScheduled();

        manager.channelClosed();
        assertThat("Keep alive ping ack timeout not cancelled.", ackTimeoutTask.promise.isCancelled(),
                is(true));

        verifyNoOtherActionPostClose(manager);
    }

    private void verifyNoOtherActionPostClose(final KeepAliveManager manager) {
        manager.channelIdle();
        verifyNoWrite();
        verifyNoScheduledTasks();

        Runnable whenInitiated = mock(Runnable.class);
        manager.initiateGracefulClose(whenInitiated);
        verify(whenInitiated, never()).run();
        verifyNoWrite();
        verifyNoScheduledTasks();
    }

    private ScheduledTask verifyPingAckTimeoutScheduled() {
        ScheduledTask ackTimeoutTask = scheduledTasks.poll();
        assertThat("Ping ack timeout not scheduled.", ackTimeoutTask, is(notNullValue()));
        assertThat("Unexpected ping ack timeout duration.", ackTimeoutTask.delayMillis,
                is(DEFAULT_ACK_TIMEOUT.toMillis()));
        return ackTimeoutTask;
    }

    private void sendGracefulClosePingAckAndVerifySecondGoAway(final KeepAliveManager manager,
                                                               final Http2PingFrame pingFrame) throws Exception {
        ScheduledTask pingAckTimeoutTask = scheduledTasks.take();
        manager.pingReceived(new DefaultHttp2PingFrame(pingFrame.content(), true));
        assertThat("Ping ack task not cancelled.", pingAckTimeoutTask.promise.isCancelled(), is(true));
        verifySecondGoAway();

        pingAckTimeoutTask.task.run();

        verifyNoWrite();
        verifyNoScheduledTasks();
    }

    private void verifySecondGoAway() {
        Http2GoAwayFrame secondGoAway = verifyWrite(instanceOf(Http2GoAwayFrame.class));
        assertThat("Unexpected error in go_away", secondGoAway.errorCode(), is(Http2Error.NO_ERROR.code()));
        assertThat("Unexpected extra stream ids", secondGoAway.extraStreamIds(), is(0));
        assertThat("Unexpected last stream id", secondGoAway.lastStreamId(), is(-1));
        verifyNoScheduledTasks();
    }

    private Http2PingFrame initiateGracefulCloseVerifyGoAwayAndPing(final KeepAliveManager manager) {
        Runnable whenInitiated = mock(Runnable.class);
        manager.initiateGracefulClose(whenInitiated);
        verify(whenInitiated).run();

        Http2GoAwayFrame firstGoAway = verifyWrite(instanceOf(Http2GoAwayFrame.class));
        assertThat("Unexpected error in go_away", firstGoAway.errorCode(), is(Http2Error.NO_ERROR.code()));
        assertThat("Unexpected extra stream ids", firstGoAway.extraStreamIds(), is(Integer.MAX_VALUE));
        assertThat("Unexpected last stream id", firstGoAway.lastStreamId(), is(-1));
        Http2PingFrame pingFrame = verifyWrite(instanceOf(Http2PingFrame.class));
        verifyNoWrite();
        return pingFrame;
    }

    @SuppressWarnings("unchecked")
    private <T> T verifyWrite(Matcher<Object> writeMatcher) {
        Object written = channel.outboundMessages().poll();
        assertThat("Unexpected frame written.", written, is(notNullValue()));
        assertThat("Unexpected frame written.", written, writeMatcher);
        return (T) written;
    }

    private void verifyNoWrite() {
        if (channel instanceof EmbeddedDuplexChannel && ((EmbeddedDuplexChannel) channel).isOutputShutdown()) {
            // EmbeddedDuplexChannel does not allow to poll messages after output shutdown.
            // It's enough to verify only output shutdown state.
            return;
        }
        Object msg = channel.outboundMessages().poll();
        if (msg instanceof ByteBuf) {
            assertThat(((ByteBuf) msg).readableBytes(), is(0));
        } else {
            assertThat("Unexpected frame written.", msg, is(nullValue()));
        }
    }

    private void verifyNoScheduledTasks() {
        assertThat("Unexpected tasks scheduled.", scheduledTasks.poll(), is(nullValue()));
    }

    private Http2StreamChannel addActiveStream(final KeepAliveManager manager) {
        Http2StreamChannel stream = mock(Http2StreamChannel.class);
        ChannelPromise closeFuture = channel.newPromise();
        when(stream.closeFuture()).thenReturn(closeFuture);
        when(stream.close()).then(__ -> {
            closeFuture.trySuccess();
            return channel.newSucceededFuture();
        });
        manager.trackActiveStream(stream);
        return stream;
    }

    private void verifyChannelCloseOnMissingPingAck(final ScheduledTask ackTimeoutTask) throws InterruptedException {
        ackTimeoutTask.task.run();
        verifyWrite(instanceOf(Http2GoAwayFrame.class));
        verifyNoScheduledTasks();
        shutdownInputIfDuplexChannel();
        assertThat("Channel not closed.", channel.isOpen(), is(false));
    }

    private KeepAliveManager newManager(final boolean allowPingWithoutActiveStreams, final Channel channel) {
        KeepAlivePolicy policy = mock(KeepAlivePolicy.class);
        when(policy.idleDuration()).thenReturn(DEFAULT_IDLE_DURATION);
        when(policy.ackTimeout()).thenReturn(DEFAULT_ACK_TIMEOUT);
        when(policy.withoutActiveStreams()).thenReturn(allowPingWithoutActiveStreams);
        return new KeepAliveManager(channel, policy,
                (task, delay, unit) -> {
                    ChannelPromise promise = channel.newPromise();
                    ScheduledTask scheduledTask = new ScheduledTask(task, promise,
                            MILLISECONDS.convert(delay, unit));
                    scheduledTasks.add(scheduledTask);
                    return scheduledTask.promise;
                },
                (__, ___, ____) -> { });
    }

    private void shutdownInputIfDuplexChannel() throws InterruptedException {
        if (channel instanceof EmbeddedDuplexChannel) {
            EmbeddedDuplexChannel duplexChannel = (EmbeddedDuplexChannel) channel;
            duplexChannel.awaitOutputShutdown();
            duplexChannel.shutdownInput().sync();   // Simulate FIN from the remote peer
        }
    }

    private static final class ScheduledTask {
        final Runnable task;
        final Promise<?> promise;
        final long delayMillis;

        ScheduledTask(final Runnable task, final Promise<?> promise, final long delayMillis) {
            this.task = task;
            this.promise = promise;
            this.delayMillis = delayMillis;
        }
    }

    private static final class KeepAliveManagerHandler extends ChannelInboundHandlerAdapter {
        private KeepAliveManager keepAliveManager;

        void keepAliveManager(KeepAliveManager keepAliveManager) {
            this.keepAliveManager = requireNonNull(keepAliveManager);
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            keepAliveManager.channelClosed();
        }

        @Override
        public void handlerRemoved(final ChannelHandlerContext ctx) {
            keepAliveManager.channelClosed();
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
            if (evt == ChannelInputShutdownReadComplete.INSTANCE || evt == SslCloseCompletionEvent.SUCCESS) {
                keepAliveManager.channelInputShutdown();
            } else if (evt == ChannelOutputShutdownEvent.INSTANCE) {
                keepAliveManager.channelOutputShutdown();
            } else {
                release(evt);
            }
        }
    }
}
