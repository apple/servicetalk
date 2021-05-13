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

import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.test.StepVerifiers;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.netty.H2ProtocolConfig.KeepAlivePolicy;

import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.util.concurrent.Promise;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.DEFAULT_ACK_TIMEOUT;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.DEFAULT_IDLE_DURATION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KeepAliveManagerTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final BlockingQueue<ScheduledTask> scheduledTasks;
    private final EmbeddedChannel channel;

    public KeepAliveManagerTest() {
        scheduledTasks = new LinkedBlockingQueue<>();
        channel = new EmbeddedChannel();
    }

    @Test
    public void keepAliveDisallowedWithNoActiveStreams() {
        KeepAliveManager manager = newManager(false);
        manager.channelIdle();
        verifyNoWrite();
        verifyNoScheduledTasks();
    }

    @Test
    public void keepAliveAllowedWithNoActiveStreams() {
        KeepAliveManager manager = newManager(true);
        manager.channelIdle();
        verifyWrite(instanceOf(Http2PingFrame.class));
    }

    @Test
    public void keepAliveWithActiveStreams() {
        KeepAliveManager manager = newManager(false);
        addActiveStream(manager);
        manager.channelIdle();
        verifyWrite(instanceOf(Http2PingFrame.class));
    }

    @Test
    public void keepAlivePingAckReceived() {
        KeepAliveManager manager = newManager(false);
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

    @Test
    public void keepAlivePingAckWithUnknownContent() {
        KeepAliveManager manager = newManager(false);
        addActiveStream(manager);
        manager.channelIdle();
        Http2PingFrame ping = verifyWrite(instanceOf(Http2PingFrame.class));
        ScheduledTask ackTimeoutTask = verifyPingAckTimeoutScheduled();

        manager.pingReceived(new DefaultHttp2PingFrame(ping.content() + 1, true));
        assertThat("Ping ack timeout task cancelled.", ackTimeoutTask.promise.isCancelled(), is(false));

        verifyChannelCloseOnMissingPingAck(ackTimeoutTask);
    }

    @Test
    public void keepAliveMissingPingAck() {
        KeepAliveManager manager = newManager(false);
        addActiveStream(manager);
        manager.channelIdle();
        verifyWrite(instanceOf(Http2PingFrame.class));
        verifyChannelCloseOnMissingPingAck(verifyPingAckTimeoutScheduled());
    }

    @Test
    public void gracefulCloseNoActiveStreams() throws Exception {
        KeepAliveManager manager = newManager(false);
        Http2PingFrame pingFrame = initiateGracefulCloseVerifyGoAwayAndPing(manager);

        sendGracefulClosePingAckAndVerifySecondGoAway(manager, pingFrame);

        channel.closeFuture().sync().await();
    }

    @Test
    public void gracefulCloseWithActiveStreams() throws Exception {
        KeepAliveManager manager = newManager(false);
        EmbeddedChannel activeStream = addActiveStream(manager);
        Http2PingFrame pingFrame = initiateGracefulCloseVerifyGoAwayAndPing(manager);

        sendGracefulClosePingAckAndVerifySecondGoAway(manager, pingFrame);

        assertThat("Channel not closed.", channel.isOpen(), is(true));
        activeStream.close().sync().await();

        channel.closeFuture().sync().await();
    }

    @Test
    public void gracefulCloseNoActiveStreamsMissingPingAck() throws Exception {
        KeepAliveManager manager = newManager(false);
        initiateGracefulCloseVerifyGoAwayAndPing(manager);

        ScheduledTask pingAckTimeoutTask = scheduledTasks.take();
        pingAckTimeoutTask.task.run();
        verifySecondGoAway();

        channel.closeFuture().sync().await();
    }

    @Test
    public void gracefulCloseActiveStreamsMissingPingAck() throws Exception {
        KeepAliveManager manager = newManager(false);
        EmbeddedChannel activeStream = addActiveStream(manager);
        initiateGracefulCloseVerifyGoAwayAndPing(manager);

        ScheduledTask pingAckTimeoutTask = scheduledTasks.take();
        pingAckTimeoutTask.task.run();
        verifySecondGoAway();

        assertThat("Channel closed.", channel.isOpen(), is(true));

        activeStream.close().sync().await();

        channel.closeFuture().sync().await();
    }

    @Test
    public void gracefulClosePendingPingsCloseConnection() throws Exception {
        KeepAliveManager manager = newManager(false);
        addActiveStream(manager);
        Http2PingFrame pingFrame = initiateGracefulCloseVerifyGoAwayAndPing(manager);

        sendGracefulClosePingAckAndVerifySecondGoAway(manager, pingFrame);
        assertThat("Channel closed.", channel.isOpen(), is(true));

        manager.channelIdle();
        verifyWrite(instanceOf(Http2PingFrame.class));
        verifyChannelCloseOnMissingPingAck(verifyPingAckTimeoutScheduled());
    }

    @Test
    public void pingsAreAcked() {
        KeepAliveManager manager = newManager(false);
        long pingContent = ThreadLocalRandom.current().nextLong();
        manager.pingReceived(new DefaultHttp2PingFrame(pingContent, false));
        Http2PingFrame pingFrame = verifyWrite(instanceOf(Http2PingFrame.class));
        assertThat("Unexpected ping ack content.", pingFrame.content(), is(pingContent));
        assertThat("Unexpected ping ack content.", pingFrame.ack(), is(true));
    }

    @Test
    public void channelClosedDuringGracefulClose() throws Exception {
        KeepAliveManager manager = newManager(false);
        addActiveStream(manager);
        initiateGracefulCloseVerifyGoAwayAndPing(manager);
        ScheduledTask pingAckTimeoutTask = scheduledTasks.take();
        assertThat("Ping ack timeout not scheduled.", pingAckTimeoutTask, is(notNullValue()));

        manager.channelClosed();
        assertThat("Graceful close ping ack timeout not cancelled.", pingAckTimeoutTask.promise.isCancelled(),
                is(true));

        verifyNoOtherActionPostClose(manager);
    }

    @Test
    public void channelClosedDuringPing() {
        KeepAliveManager manager = newManager(false);
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

        CompletableSource.Processor onClosing = newCompletableProcessor();
        manager.initiateGracefulClose(onClosing);
        verifyNoWrite();
        verifyNoScheduledTasks();
        StepVerifiers.createForSource(onClosing).expectCancellable().expectComplete().verify();
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
        verifyNoScheduledTasks();
    }

    private Http2PingFrame initiateGracefulCloseVerifyGoAwayAndPing(final KeepAliveManager manager) {
        CompletableSource.Processor onClosing = newCompletableProcessor();
        manager.initiateGracefulClose(onClosing);
        StepVerifiers.createForSource(onClosing).expectCancellable().expectComplete().verify();

        Http2GoAwayFrame firstGoAway = verifyWrite(instanceOf(Http2GoAwayFrame.class));
        assertThat("Unexpected error in go_away", firstGoAway.errorCode(), is(Http2Error.NO_ERROR.code()));
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
        assertThat("Unexpected frame written.", channel.outboundMessages().poll(), is(nullValue()));
    }

    private void verifyNoScheduledTasks() {
        assertThat("Unexpected tasks scheduled.", scheduledTasks.poll(), is(nullValue()));
    }

    private static EmbeddedChannel addActiveStream(final KeepAliveManager manager) {
        EmbeddedChannel stream = new EmbeddedChannel();
        manager.trackActiveStream(stream);
        return stream;
    }

    private void verifyChannelCloseOnMissingPingAck(final ScheduledTask ackTimeoutTask) {
        ackTimeoutTask.task.run();
        verifyWrite(instanceOf(Http2GoAwayFrame.class));
        verifyNoScheduledTasks();
        assertThat("Channel not closed.", channel.isOpen(), is(false));
    }

    private KeepAliveManager newManager(final boolean allowPingWithoutActiveStreams) {
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
}
