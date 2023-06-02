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

import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.http.netty.H2ProtocolConfig.KeepAlivePolicy;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.netty.internal.ConnectionObserverInitializer;
import io.servicetalk.transport.netty.internal.EmbeddedDuplexChannel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.DEFAULT_IDLE_DURATION;
import static io.servicetalk.http.netty.KeepAliveManager.GC_TIMEOUT_GO_AWAY_CONTENT;
import static io.servicetalk.http.netty.KeepAliveManager.KA_TIMEOUT_GO_AWAY_CONTENT;
import static io.servicetalk.http.netty.KeepAliveManager.LOCAL_GO_AWAY_CONTENT;
import static io.servicetalk.http.netty.KeepAliveManager.SECOND_GO_AWAY_CONTENT;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofMillis;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeepAliveManagerTest {

    private static final Duration ACK_TIMEOUT = CI ? ofMillis(1000) : ofMillis(200);

    private final BlockingQueue<ScheduledTask> scheduledTasks = new LinkedBlockingQueue<>();
    private final AtomicBoolean failWrite = new AtomicBoolean();
    private final ConnectionObserver connectionObserver = mock(ConnectionObserver.class);
    private EmbeddedChannel channel;
    private KeepAliveManager manager;

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    private void setUp(boolean duplex, boolean allowPingWithoutActiveStreams) {
        KeepAliveManagerHandler managerHandler = new KeepAliveManagerHandler();
        FailWriteHandler failWriteHandler = new FailWriteHandler();
        channel = duplex ? new EmbeddedDuplexChannel(true, failWriteHandler, managerHandler)
                : new EmbeddedChannel(failWriteHandler, managerHandler);
        new ConnectionObserverInitializer(connectionObserver, false, false).init(channel);
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
        verifyKeepAlivePingFrame();
        verifyPingAckTimeoutScheduled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void keepAliveWithActiveStreams(boolean duplex) {
        setUp(duplex, false);
        addActiveStream(manager);
        manager.channelIdle();
        verifyKeepAlivePingFrame();
        verifyPingAckTimeoutScheduled();
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void keepAlivePingAckReceived(boolean duplex) {
        setUp(duplex, false);
        addActiveStream(manager);
        manager.channelIdle();
        Http2PingFrame ping = verifyKeepAlivePingFrame();
        ScheduledTask ackTimeoutTask = verifyPingAckTimeoutScheduled();

        manager.pingReceived(new DefaultHttp2PingFrame(ping.content(), true));
        assertThat("Ping ack timeout task not cancelled.", ackTimeoutTask.promise.isCancelled(), is(true));

        ackTimeoutTask.runTask();
        verifyNoWrite();
        verifyNoScheduledTasks();
        assertThat("Channel unexpectedly closed.", channel.isOpen(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void keepAlivePingAckWithUnknownContent(boolean duplex) throws Exception {
        setUp(duplex, false);
        Http2StreamChannel activeStream = addActiveStream(manager);
        manager.channelIdle();
        Http2PingFrame ping = verifyKeepAlivePingFrame();
        ScheduledTask ackTimeoutTask = verifyPingAckTimeoutScheduled();

        manager.pingReceived(new DefaultHttp2PingFrame(ping.content() + 1, true));
        assertThat("Ping ack timeout task cancelled.", ackTimeoutTask.promise.isCancelled(), is(false));

        verifyChannelCloseOnMissingPingAck(ackTimeoutTask, duplex);
        activeStream.closeFuture().await();
        verifyConnectionObserver(TimeoutException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void keepAliveMissingPingAck(boolean duplex) throws Exception {
        setUp(duplex, false);
        Http2StreamChannel activeStream = addActiveStream(manager);
        manager.channelIdle();
        verifyKeepAlivePingFrame();
        verifyChannelCloseOnMissingPingAck(verifyPingAckTimeoutScheduled(), duplex);
        activeStream.closeFuture().await();
        verifyConnectionObserver(TimeoutException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void gracefulCloseNoActiveStreams(boolean duplex) throws Exception {
        setUp(duplex, false);
        Http2PingFrame pingFrame = initiateGracefulCloseVerifyGoAwayAndPing(manager);

        sendGracefulClosePingAckAndVerifySecondGoAway(manager, pingFrame, duplex);
        shutdownInputIfDuplexChannel();
        channel.closeFuture().await();
        verifyConnectionObserver(null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void gracefulCloseWithActiveStreams(boolean duplex) throws Exception {
        setUp(duplex, false);
        Http2StreamChannel activeStream = addActiveStream(manager);
        Http2PingFrame pingFrame = initiateGracefulCloseVerifyGoAwayAndPing(manager);

        sendGracefulClosePingAckAndVerifySecondGoAway(manager, pingFrame, duplex);

        assertThat("Channel not closed.", channel.isOpen(), is(true));
        activeStream.close().sync().await();
        shutdownInputIfDuplexChannel();
        channel.closeFuture().await();
        verifyConnectionObserver(null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void gracefulCloseNoActiveStreamsMissingPingAck(boolean duplex) throws Exception {
        setUp(duplex, false);
        initiateGracefulCloseVerifyGoAwayAndPing(manager);

        ScheduledTask pingAckTimeoutTask = scheduledTasks.take();
        pingAckTimeoutTask.runTask();
        verifySecondGoAway(duplex, GC_TIMEOUT_GO_AWAY_CONTENT);
        shutdownInputIfDuplexChannel();
        channel.closeFuture().await();
        verifyConnectionObserver(TimeoutException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void gracefulCloseActiveStreamsMissingPingAck(boolean duplex) throws Exception {
        setUp(duplex, false);
        Http2StreamChannel activeStream = addActiveStream(manager);
        initiateGracefulCloseVerifyGoAwayAndPing(manager);

        ScheduledTask pingAckTimeoutTask = scheduledTasks.take();
        pingAckTimeoutTask.runTask();
        verifySecondGoAway(duplex, GC_TIMEOUT_GO_AWAY_CONTENT);

        channel.closeFuture().await();
        activeStream.closeFuture().await();
        verifyConnectionObserver(TimeoutException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void gracefulClosePendingPingsCloseConnection(boolean duplex) throws Exception {
        setUp(duplex, false);
        Http2StreamChannel activeStream = addActiveStream(manager);
        Http2PingFrame pingFrame = initiateGracefulCloseVerifyGoAwayAndPing(manager);

        sendGracefulClosePingAckAndVerifySecondGoAway(manager, pingFrame, duplex);
        assertThat("Channel closed.", channel.isOpen(), is(true));

        manager.channelIdle();
        verifyKeepAlivePingFrame();
        verifyChannelCloseOnMissingPingAck(verifyPingAckTimeoutScheduled(), duplex);
        activeStream.closeFuture().await();
        verifyConnectionObserver(TimeoutException.class);
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
        verifyKeepAlivePingFrame();
        ScheduledTask ackTimeoutTask = verifyPingAckTimeoutScheduled();

        manager.channelClosed();
        assertThat("Keep alive ping ack timeout not cancelled.", ackTimeoutTask.promise.isCancelled(),
                is(true));

        verifyNoOtherActionPostClose(manager);
    }

    @Test
    void duplexGracefulCloseNoInputShutdown() throws Exception {
        setUp(true, false);
        Http2PingFrame pingFrame = initiateGracefulCloseVerifyGoAwayAndPing(manager);

        sendGracefulClosePingAckAndVerifySecondGoAway(manager, pingFrame, true);
        EmbeddedDuplexChannel duplexChannel = (EmbeddedDuplexChannel) channel;
        duplexChannel.awaitOutputShutdown();
        // Don't shutdown input, verify that timeout will force channel closure.
        // Use CountDownLatch instead of channel.closeFuture().await() to avoid BlockingOperationException.
        CountDownLatch closeLatch = new CountDownLatch(1);
        channel.closeFuture().addListener(f -> closeLatch.countDown());
        assertThat("Channel closed unexpectedly",
                closeLatch.await(ACK_TIMEOUT.toMillis(), MILLISECONDS), is(false));
        ScheduledTask inputShutdownTimeoutTask = scheduledTasks.take();
        inputShutdownTimeoutTask.runTask();
        closeLatch.await();
        channel.closeFuture().await();
        verifyConnectionObserver(TimeoutException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void failureToWritePingClosesChannel(boolean duplex) throws Exception {
        setUp(duplex, true);
        failWrite.set(true);
        manager.channelIdle();
        channel.closeFuture().await();
        verifyConnectionObserver(DeliberateException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void failureToWriteLastGoAwayAfterPingAckTimeoutClosesChannel(boolean duplex) throws Exception {
        setUp(duplex, true);
        manager.channelIdle();
        verifyKeepAlivePingFrame();
        ScheduledTask ackTimeoutTask = verifyPingAckTimeoutScheduled();
        failWrite.set(true);
        ackTimeoutTask.runTask();
        channel.closeFuture().await();
        verifyConnectionObserver(DeliberateException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void failureToWriteFirstGoAwayClosesChannel(boolean duplex) throws Exception {
        setUp(duplex, true);
        failWrite.set(true);
        initiateGracefulClose(manager);
        channel.closeFuture().await();
        verifyConnectionObserver(DeliberateException.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] duplex={0}")
    @ValueSource(booleans = {true, false})
    void failureToWriteSecondGoAwayClosesChannel(boolean duplex) throws Exception {
        setUp(duplex, true);
        Http2PingFrame pingFrame = initiateGracefulCloseVerifyGoAwayAndPing(manager);
        failWrite.set(true);
        manager.pingReceived(new DefaultHttp2PingFrame(pingFrame.content(), true));
        channel.closeFuture().await();
        verifyConnectionObserver(DeliberateException.class);
    }

    private void verifyNoOtherActionPostClose(final KeepAliveManager manager) {
        manager.channelIdle();
        verifyNoWrite();
        verifyNoScheduledTasks();

        Runnable whenInitiated = mock(Runnable.class);
        manager.initiateGracefulClose(whenInitiated, true);
        verify(whenInitiated, never()).run();
        verifyNoWrite();
        verifyNoScheduledTasks();
    }

    private ScheduledTask verifyPingAckTimeoutScheduled() {
        ScheduledTask ackTimeoutTask = scheduledTasks.poll();
        assertThat("Ping ack timeout not scheduled.", ackTimeoutTask, is(notNullValue()));
        assertThat("Unexpected ping ack timeout duration.", ackTimeoutTask.delayMillis,
                is(ACK_TIMEOUT.toMillis()));
        return ackTimeoutTask;
    }

    private void sendGracefulClosePingAckAndVerifySecondGoAway(final KeepAliveManager manager,
                                                               final Http2PingFrame pingFrame,
                                                               final boolean duplex) throws Exception {
        ScheduledTask pingAckTimeoutTask = scheduledTasks.take();
        manager.pingReceived(new DefaultHttp2PingFrame(pingFrame.content(), true));
        assertThat("Ping ack task not cancelled.", pingAckTimeoutTask.promise.isCancelled(), is(true));
        verifySecondGoAway(duplex, SECOND_GO_AWAY_CONTENT);

        pingAckTimeoutTask.runTask();

        verifyNoWrite();
        if (duplex) {
            verifyAtMostOneScheduledTasks();
        } else {
            verifyNoScheduledTasks();
        }
    }

    private void verifySecondGoAway(boolean duplex, ByteBuf expectedContent) {
        Http2GoAwayFrame secondGoAway = verifyWrite(instanceOf(Http2GoAwayFrame.class));
        assertThat("Unexpected error in go_away", secondGoAway.errorCode(), is(Http2Error.NO_ERROR.code()));
        assertThat("Unexpected extra stream ids", secondGoAway.extraStreamIds(), is(0));
        assertThat("Unexpected last stream id", secondGoAway.lastStreamId(), is(-1));
        assertThat("Unexpected content", secondGoAway.content().toString(US_ASCII),
                is(expectedContent.toString(US_ASCII)));
        if (duplex) {
            verifyAtMostOneScheduledTasks();
        } else {
            verifyNoScheduledTasks();
        }
    }

    private Http2PingFrame initiateGracefulCloseVerifyGoAwayAndPing(final KeepAliveManager manager) {
        initiateGracefulClose(manager);

        Http2GoAwayFrame firstGoAway = verifyWrite(instanceOf(Http2GoAwayFrame.class));
        assertThat("Unexpected error in go_away", firstGoAway.errorCode(), is(Http2Error.NO_ERROR.code()));
        assertThat("Unexpected extra stream ids", firstGoAway.extraStreamIds(), is(Integer.MAX_VALUE));
        assertThat("Unexpected last stream id", firstGoAway.lastStreamId(), is(-1));
        assertThat("Unexpected content", firstGoAway.content().toString(US_ASCII),
                is(LOCAL_GO_AWAY_CONTENT.toString(US_ASCII)));
        Http2PingFrame pingFrame = verifyGracefulClosePingFrame();
        verifyNoWrite();
        return pingFrame;
    }

    private Http2PingFrame verifyGracefulClosePingFrame() {
        return verifyPingFrame(false);
    }

    private Http2PingFrame verifyKeepAlivePingFrame() {
        return verifyPingFrame(true);
    }

    private Http2PingFrame verifyPingFrame(boolean even) {
        Http2PingFrame pingFrame = verifyWrite(instanceOf(Http2PingFrame.class));
        assertThat("Unexpected ping ack content.", pingFrame.content() % 2 == 0, is(even));
        assertThat("Unexpected ping ack flag.", pingFrame.ack(), is(false));
        return pingFrame;
    }

    private void initiateGracefulClose(final KeepAliveManager manager) {
        Runnable whenInitiated = mock(Runnable.class);
        manager.initiateGracefulClose(whenInitiated, true);
        verify(whenInitiated).run();
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

    private void verifyAtMostOneScheduledTasks() {
        assertThat(scheduledTasks.size(), lessThanOrEqualTo(1));
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
        channel.closeFuture().addListener(f -> stream.close());
        return stream;
    }

    private void verifyChannelCloseOnMissingPingAck(final ScheduledTask ackTimeoutTask, boolean duplex)
            throws InterruptedException {
        ackTimeoutTask.runTask();
        verifySecondGoAway(duplex, KA_TIMEOUT_GO_AWAY_CONTENT);
        shutdownInputIfDuplexChannel();
        assertThat("Channel not closed.", channel.isOpen(), is(false));
    }

    private KeepAliveManager newManager(final boolean allowPingWithoutActiveStreams, final Channel channel) {
        KeepAlivePolicy policy = mock(KeepAlivePolicy.class);
        when(policy.idleDuration()).thenReturn(DEFAULT_IDLE_DURATION);
        when(policy.ackTimeout()).thenReturn(ACK_TIMEOUT);
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
            if (!duplexChannel.isInputShutdown()) { // we may have forced input shutdown already due to timeout
                duplexChannel.shutdownInput().sync();   // Simulate FIN from the remote peer
            }
        }
    }

    private void verifyConnectionObserver(@Nullable Class<? extends Throwable> exceptionClass) {
        if (exceptionClass != null) {
            verify(connectionObserver).connectionClosed(any(exceptionClass));
            verify(connectionObserver, never()).connectionClosed();
        } else {
            verify(connectionObserver).connectionClosed();
            verify(connectionObserver, never()).connectionClosed(any(Throwable.class));
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

        void runTask() {
            if (promise.isCancelled()) {
                return;
            }
            task.run();
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

    private final class FailWriteHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            if (failWrite.get()) {
                release(msg);
                promise.tryFailure(DELIBERATE_EXCEPTION);
                return;
            }
            ctx.write(msg, promise);
        }
    }
}
